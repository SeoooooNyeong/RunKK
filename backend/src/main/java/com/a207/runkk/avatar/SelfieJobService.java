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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 외부 호출을 짧은 예약·완료 트랜잭션 사이에서 수행한다.
 *
 * <p>결과가 불명확하게 실패한 작업(RECOVERABLE)은 접수 뒤 {@link #RECOVERY_WINDOW} 동안 상태를 다시 물어, ai(Node)가 그 뒤 저장한
 * 머리를 성공으로 회수했으면 진행 중으로 되돌려 결과를 받는다(유료 재생성 없음, DV-9). 같은 사진의 재전송은 같은 작업을 돌려주고(멱등), 실패가 확정된 뒤 같은 사진을
 * 다시 고르면 시도 번호를 올려 새 작업을 만든다. 같은 사진으로 다시 해도 결과가 같은 실패(사진 품질)는 새로 만들지 않는다.
 */
@Service
public class SelfieJobService {
    /** Node가 결과를 확정하지 못하고 끝낸 사유. 생성은 계속 돌아 머리를 저장했을 수 있다. */
    static final Set<String> RECOVERABLE = Set.of("GENERATION_OUTCOME_UNKNOWN", "UPSTREAM_TIMEOUT");

    /** 같은 사진으로 다시 해도 같은 결과인 실패. 새 시도를 만들지 않는다. */
    static final Set<String> SAME_PHOTO_FAILS_AGAIN =
            Set.of("SELFIE_QUALITY_REJECTED", "NO_FACE_DETECTED");

    /** 회수를 확인하는 기간. Node 생성 제한(15분)과 그 뒤 저장까지 넉넉히 둔다. */
    static final Duration RECOVERY_WINDOW = Duration.ofHours(1);

    /** 같은 사진 시도 상한. ai 라우터 attempt 범위(1~20)와 같다. */
    static final int MAX_ATTEMPTS = 20;

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
        int number = attemptFor(memberId, image);
        String requestId = requestId(memberId, image, number);
        SelfieJob reservation = tx.write(() -> reserve(memberId, requestId));
        if (reservation.jobId() != null) return status(memberId, reservation.jobId());
        LocalDateTime attempt = reservation.attemptedAt();
        FaceJob accepted;
        try {
            accepted = gateway.submit(memberId, image, number);
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

    /**
     * 이번 접수의 시도 번호. 같은 사진의 마지막 시도가 실패로 확정됐고 같은 사진으로 다시 해 볼 만하면 다음 번호다. 그 실패가 회수될 수 있으면 먼저 상태를 다시 물어
     * 회수됐으면 그 시도를 쓴다(다시 만들지 않는다).
     */
    private int attemptFor(long memberId, byte[] image) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String requestId = requestId(memberId, image, attempt);
            SelfieJob known = tx.read(() -> jobs.find(requestId).orElse(null));
            if (known == null || !failedForGood(known)) return attempt;
            if (SAME_PHOTO_FAILS_AGAIN.contains(known.errorCode())) return attempt;
            if (recoverable(known)
                    && status(memberId, known.jobId()).status() != FaceJob.Status.FAILED) {
                return attempt;
            }
        }
        throw new BusinessRuleException(ReasonCode.SELFIE_RETRY_LIMIT);
    }

    private static boolean failedForGood(SelfieJob job) {
        return job.finished()
                && job.jobId() != null
                && job.view().status() == FaceJob.Status.FAILED;
    }

    private boolean recoverable(SelfieJob job) {
        return failedForGood(job)
                && RECOVERABLE.contains(job.errorCode())
                && job.attemptedAt().plus(RECOVERY_WINDOW).isAfter(LocalDateTime.now(clock));
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
        if (recoverable(local)) return recover(memberId, local);
        if (local.finished()) return local.view();
        try {
            return reconcile(memberId, gateway.status(memberId, jobId));
        } catch (FaceJobRejectedException error) {
            recoverMissing(memberId, jobId, error);
            throw error;
        }
    }

    /**
     * 결과가 불명확하게 실패한 작업의 상태를 다시 묻는다. ai가 성공으로 회수했고, 그사이 기본 캐릭터를 고르거나 다른 셀카를 보내지 않았으면 진행 중으로 되돌린다.
     * 조회가 실패하면 마지막으로 본 실패를 그대로 돌려준다.
     */
    private FaceJob recover(long memberId, SelfieJob local) {
        FaceJob remote;
        try {
            remote = gateway.status(memberId, local.jobId());
        } catch (FaceJobRejectedException error) {
            return local.view();
        }
        if (remote.status() != FaceJob.Status.SUCCEEDED) return local.view();
        return tx.write(
                () -> {
                    Avatar avatar = lockedActiveAvatar(memberId);
                    SelfieJob job = jobs.byJob(memberId, local.jobId()).orElseThrow();
                    if (!recoverable(job)
                            || !avatar.awaitsFaceJob(job.requestId())
                            || jobs.pending(memberId).isPresent()) {
                        return job.view();
                    }
                    job.reopen();
                    job.accepted(remote);
                    jobs.save(job);
                    return job.view();
                });
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

    /** 시도 1은 예전 키와 같다(회원 + 사진). 다음 시도는 번호를 섞는다. */
    private static String requestId(long memberId, byte[] image, int attempt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String prefix = attempt == 1 ? memberId + ":" : memberId + ":" + attempt + ":";
            digest.update(prefix.getBytes(StandardCharsets.UTF_8));
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
