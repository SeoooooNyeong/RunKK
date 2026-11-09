package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.OperationPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 외부 호출을 짧은 예약·완료 트랜잭션 사이에서 수행한다. */
@Service
public class SelfieJobService {
    private final FaceJobGateway gateway;
    private final SelfieJobRepository jobs;
    private final AvatarRepository avatars;
    private final MemberQueryService members;
    private final OperationPolicy policy;
    private final SelfieFailureStore failures;
    private final AvatarTransactions tx;
    private final Clock clock;

    public SelfieJobService(
            FaceJobGateway gateway,
            SelfieJobRepository jobs,
            AvatarRepository avatars,
            MemberQueryService members,
            OperationPolicy policy,
            SelfieFailureStore failures,
            AvatarTransactions tx,
            Clock clock) {
        this.gateway = gateway;
        this.jobs = jobs;
        this.avatars = avatars;
        this.members = members;
        this.policy = policy;
        this.failures = failures;
        this.tx = tx;
        this.clock = clock;
    }

    public FaceJob submit(long memberId, byte[] image) {
        if (image == null || image.length == 0) throw new IllegalArgumentException("빈 셀카");
        if (image.length > 1024 * 1024) throw new FaceJobRejectedException(413, "TOO_LARGE");
        if (!policy.features().selfieEnabled())
            throw new BusinessRuleException(ReasonCode.SELFIE_DISABLED);
        requireConsent(memberId);
        String requestId = requestId(memberId, image);
        SelfieJob reservation = tx.write(() -> reserve(memberId, requestId));
        if (reservation.jobId() != null) return status(memberId, reservation.jobId());
        LocalDateTime attempt = reservation.attemptedAt();
        FaceJob accepted;
        try {
            accepted = gateway.submit(memberId, image);
        } catch (FaceJobRejectedException error) {
            if (error.status() < 500) {
                tx.write(
                        () -> {
                            lockedActiveAvatar(memberId);
                            SelfieJob job = jobs.find(requestId).orElseThrow();
                            if (job.finished() || !attempt.equals(job.attemptedAt())) return job;
                            job.finish();
                            jobs.save(job);
                            failures.recordFailure(memberId, LocalDate.now(clock));
                            return job;
                        });
            }
            // 503·504는 접수됐는지 모른다. 같은 사진으로 재전송하면 AI 중복 방지로 복구한다.
            throw error;
        }
        tx.write(
                () -> {
                    Avatar avatar = lockedActiveAvatar(memberId);
                    SelfieJob job = jobs.find(requestId).orElseThrow();
                    if (job.finished() || !attempt.equals(job.attemptedAt())) {
                        throw new BusinessRuleException(ReasonCode.SELFIE_IN_PROGRESS);
                    }
                    job.accepted(accepted);
                    jobs.save(job);
                    avatars.save(avatar);
                    return job;
                });
        return reconcile(memberId, accepted);
    }

    private SelfieJob reserve(long memberId, String requestId) {
        Avatar avatar = lockedActiveAvatar(memberId);
        requireConsent(memberId);
        SelfieJob known = jobs.find(requestId).orElse(null);
        if (known != null && known.jobId() != null) return known;
        SelfieJob pending = jobs.pending(memberId).orElse(null);
        if (pending != null
                && pending.jobId() == null
                && !pending.submissionInProgress(LocalDateTime.now(clock))) {
            pending.finish();
            jobs.save(pending);
            pending = null;
        }
        if (pending != null
                && (!pending.requestId().equals(requestId)
                        || pending.submissionInProgress(LocalDateTime.now(clock)))) {
            throw new BusinessRuleException(ReasonCode.SELFIE_IN_PROGRESS);
        }
        LocalDate today = LocalDate.now(clock);
        if (!avatar.canCreateSelfie(today, policy.selfieLimit())) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        if (failures.failuresOn(memberId, today) >= 5) {
            throw new BusinessRuleException(ReasonCode.SELFIE_RETRY_LIMIT);
        }
        SelfieJob job =
                known == null
                        ? new SelfieJob(requestId, memberId, LocalDateTime.now(clock))
                        : known;
        job.retryAt(LocalDateTime.now(clock));
        jobs.save(job);
        avatar.beginFaceJob(requestId);
        avatars.save(avatar);
        return job;
    }

    public FaceJob status(long memberId, String jobId) {
        SelfieJob local = ownJob(memberId, jobId);
        if (local.finished()) return local.view();
        try {
            return reconcile(memberId, gateway.status(memberId, jobId));
        } catch (FaceJobRejectedException error) {
            recoverMissing(memberId, jobId, error);
            throw error;
        }
    }

    private FaceJob reconcile(long memberId, FaceJob remote) {
        return tx.write(
                () -> {
                    lockedActiveAvatar(memberId);
                    SelfieJob local = jobs.byJob(memberId, remote.jobId()).orElseThrow();
                    if (!local.finished()) {
                        local.accepted(remote);
                        if (remote.status() == FaceJob.Status.FAILED) {
                            local.finish();
                            failures.recordFailure(memberId, LocalDate.now(clock));
                        }
                        jobs.save(local);
                    }
                    return local.view();
                });
    }

    public GeneratedHead result(long memberId, String jobId) {
        SelfieJob local = ownJob(memberId, jobId);
        if (local.finished() && local.view().status() == FaceJob.Status.FAILED) {
            throw new FaceJobRejectedException(409, "JOB_NOT_READY");
        }
        GeneratedHead head;
        try {
            head = gateway.result(memberId, jobId);
        } catch (FaceJobRejectedException error) {
            recoverMissing(memberId, jobId, error);
            throw error;
        }
        SelfieJob applied =
                tx.write(
                        () -> {
                            Avatar avatar = lockedActiveAvatar(memberId);
                            requireConsent(memberId);
                            SelfieJob job = jobs.byJob(memberId, jobId).orElseThrow();
                            if (job.jobId() != null
                                    && job.view().headId() != null
                                    && !job.view().headId().equals(head.headId())) {
                                throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
                            }
                            if (job.finished()) return job;
                            LocalDate today = LocalDate.now(clock);
                            if (!avatar.canCreateSelfie(today, policy.selfieLimit())) {
                                // 반영하지 못할 결과는 작업을 끝내 둔다. 롤백하면 끝나지 않은 작업이 남아 새 셀카가 계속 막힌다.
                                job.accepted(
                                        new FaceJob(
                                                jobId,
                                                FaceJob.Status.FAILED,
                                                null,
                                                new FaceJob.Error(
                                                        ReasonCode.SELFIE_LIMIT.name(), null)));
                                job.finish();
                                avatar.endFaceJob(job.requestId());
                                avatars.save(avatar);
                                jobs.save(job);
                                return job;
                            }
                            avatar.applyGeneratedHead(
                                    job.requestId(), jobId, today, policy.selfieLimit());
                            job.accepted(
                                    new FaceJob(
                                            jobId, FaceJob.Status.SUCCEEDED, head.headId(), null));
                            job.finish();
                            avatars.save(avatar);
                            jobs.save(job);
                            return job;
                        });
        if (applied.view().status() == FaceJob.Status.FAILED) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        return head;
    }

    /**
     * 앱이 이어 조회할 내 셀카 작업: 접수돼 작업 ID가 있고 아직 끝내지 않은 것(QUEUED·RUNNING, 또는 SUCCEEDED인데 결과를 받지 않아 얼굴에
     * 반영하지 않은 것). 앱이 작업 ID를 잃어도 이것으로 이어 조회·마무리한다. 외부 호출 없이 마지막으로 본 상태를 돌려준다.
     */
    public Optional<FaceJob> current(long memberId) {
        return tx.read(
                () ->
                        jobs.pending(memberId)
                                .filter(job -> job.jobId() != null)
                                .map(SelfieJob::view));
    }

    public GeneratedHead latest(long memberId) {
        return gateway.latest(memberId);
    }

    public GeneratedHead head(long viewerId, long ownerId, String jobId) {
        DisclosureLevel level =
                members.disclosureOf(viewerId, List.of(ownerId))
                        .getOrDefault(ownerId, DisclosureLevel.WITHDRAWN);
        if (level == DisclosureLevel.WITHDRAWN)
            throw new PermissionDeniedException("탈퇴했거나 없는 회원의 얼굴");
        SelfieJob job = ownJob(ownerId, jobId);
        if (!job.finished() || job.view().status() != FaceJob.Status.SUCCEEDED) {
            throw new FaceJobRejectedException(404, "HEAD_NOT_FOUND");
        }
        return gateway.result(ownerId, jobId);
    }

    public AvatarBody body(String bodyId) {
        return gateway.body(bodyId);
    }

    private SelfieJob ownJob(long memberId, String jobId) {
        return tx.read(
                () ->
                        jobs.byJob(memberId, jobId)
                                .orElseThrow(
                                        () -> new FaceJobRejectedException(404, "JOB_NOT_FOUND")));
    }

    private void recoverMissing(long memberId, String jobId, FaceJobRejectedException error) {
        if (error.status() != 404 || !error.reason().equals("JOB_NOT_FOUND")) return;
        tx.write(
                () -> {
                    lockedActiveAvatar(memberId);
                    SelfieJob job = jobs.byJob(memberId, jobId).orElseThrow();
                    if (!job.finished()) {
                        job.missing();
                        jobs.save(job);
                    }
                    return job;
                });
    }

    private Avatar lockedActiveAvatar(long memberId) {
        Avatar avatar = avatars.findForUpdate(memberId).orElseThrow();
        if (!members.isActive(memberId)) throw new PermissionDeniedException("탈퇴했거나 없는 회원");
        return avatar;
    }

    private void requireConsent(long memberId) {
        if (!members.hasConsent(memberId, ConsentItem.FACE_TRANSFER)) {
            throw new BusinessRuleException(ReasonCode.FACE_CONSENT_REQUIRED);
        }
    }

    private static String requestId(long memberId, byte[] image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((memberId + ":").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(image));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256를 사용할 수 없다", error);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn event) {
        jobs.deleteByMember(event.memberId());
    }
}
