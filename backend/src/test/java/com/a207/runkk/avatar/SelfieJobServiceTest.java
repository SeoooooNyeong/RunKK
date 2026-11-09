package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SelfieLimit;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfieJobServiceTest {
    private final FaceJobGateway gateway = mock(FaceJobGateway.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private final AvatarFakes.Failures failures = new AvatarFakes.Failures();
    private final MemoryJobs jobs = new MemoryJobs();
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final FaceJob queued = new FaceJob("job-1", FaceJob.Status.QUEUED, null, null);
    private final GeneratedHead head =
            new GeneratedHead(
                    "head-1", "1", "face", "eyes", "hair", Map.of(), "short", Map.of(), Map.of(),
                    "now");
    private SelfieJobService service;

    @BeforeEach
    void setup() {
        when(members.isActive(anyLong())).thenReturn(true);
        when(members.hasConsent(anyLong(), eq(ConsentItem.FACE_TRANSFER))).thenReturn(true);
        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        when(policy.selfieLimit()).thenReturn(new SelfieLimit(1));
        when(gateway.submit(eq(1L), any())).thenReturn(queued);
        when(gateway.status(1, "job-1")).thenReturn(queued);
        when(gateway.result(1, "job-1")).thenReturn(head);
        service = service(clock);
    }

    private SelfieJobService service(Clock time) {
        return new SelfieJobService(
                gateway,
                jobs,
                avatars,
                members,
                policy,
                failures,
                AvatarFakes.transactions(),
                time);
    }

    @Test
    void submittingAndPollingDoesNotSpendQuota() {
        assertThat(service.submit(1, new byte[] {1})).isEqualTo(queued);
        assertThat(service.status(1, "job-1")).isEqualTo(queued);
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void samePhotoReturnsSameJobWithoutSubmittingTwice() {
        service.submit(1, new byte[] {1});
        assertThat(service.submit(1, new byte[] {1}).jobId()).isEqualTo("job-1");
        verify(gateway, times(1)).submit(eq(1L), any());
    }

    @Test
    void differentPhotoIsBlockedWhileOneIsPending() {
        service.submit(1, new byte[] {1});
        assertThatThrownBy(() -> service.submit(1, new byte[] {2}))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SELFIE_IN_PROGRESS));
        verify(gateway, times(1)).submit(eq(1L), any());
    }

    @Test
    void completedJobIsAppliedOnceAcrossPollingResultAndResubmission() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        service.status(1, "job-1");
        service.result(1, "job-1");
        service.submit(1, new byte[] {1});
        assertThat(avatars.findOrCreate(1).creationCount()).isEqualTo(1);
        assertThat(avatars.findOrCreate(1).face().orElseThrow().renderKey())
                .isEqualTo("head:1:job-1");
        assertThatThrownBy(() -> service.submit(1, new byte[] {2}))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SELFIE_LIMIT));
    }

    @Test
    void failedJobKeepsErrorAndCountsFailureOnce() {
        service.submit(1, new byte[] {1});
        FaceJob failed =
                new FaceJob(
                        "job-1",
                        FaceJob.Status.FAILED,
                        null,
                        new FaceJob.Error("NO_FACE_DETECTED", "얼굴 없음"));
        when(gateway.status(1, "job-1")).thenReturn(failed);
        assertThat(service.status(1, "job-1")).isEqualTo(failed);
        service.status(1, "job-1");
        assertThat(failures.failuresOn(1, LocalDate.now(clock))).isEqualTo(1);
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void anotherMemberCannotPollOrReadResult() {
        service.submit(1, new byte[] {1});
        assertThatThrownBy(() -> service.status(2, "job-1"))
                .isInstanceOf(FaceJobRejectedException.class);
        assertThatThrownBy(() -> service.result(2, "job-1"))
                .isInstanceOf(FaceJobRejectedException.class);
        verify(gateway, never()).status(2, "job-1");
        verify(gateway, never()).result(2, "job-1");
    }

    @Test
    void choosingDefaultDuringGenerationIsNotOverwritten() {
        service.submit(1, new byte[] {1});
        avatars.findForUpdate(1).orElseThrow().chooseDefault(DefaultCharacter.FOX);
        service.result(1, "job-1");
        assertThat(avatars.findOrCreate(1).face().orElseThrow().renderKey())
                .isEqualTo("default:FOX");
        assertThat(avatars.findOrCreate(1).creationCount()).isEqualTo(1);
    }

    @Test
    void revokedConsentPreventsApplyingGeneratedFace() {
        service.submit(1, new byte[] {1});
        when(members.hasConsent(1, ConsentItem.FACE_TRANSFER)).thenReturn(false);
        assertThatThrownBy(() -> service.result(1, "job-1"))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.FACE_CONSENT_REQUIRED));
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void currentJobIsTheAcceptedUnfinishedOneUntilResultIsApplied() {
        assertThat(service.current(1)).isEmpty();
        service.submit(1, new byte[] {1});
        assertThat(service.current(1)).contains(queued);
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        service.status(1, "job-1");
        assertThat(service.current(1).orElseThrow().status()).isEqualTo(FaceJob.Status.SUCCEEDED);
        assertThat(service.current(2)).isEmpty();
        service.result(1, "job-1");
        assertThat(service.current(1)).isEmpty();
    }

    @Test
    void failedJobIsNotCurrent() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1"))
                .thenReturn(
                        new FaceJob(
                                "job-1",
                                FaceJob.Status.FAILED,
                                null,
                                new FaceJob.Error("NO_FACE_DETECTED", "얼굴 없음")));
        service.status(1, "job-1");
        assertThat(service.current(1)).isEmpty();
    }

    @Test
    void uncertainSubmissionWithoutJobIdIsNotCurrent() {
        when(gateway.submit(eq(1L), any())).thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(FaceJobRejectedException.class);
        assertThat(service.current(1)).isEmpty();
    }

    @Test
    void resultOverDailyLimitFinishesJobSoMemberIsNotBlocked() {
        service.submit(1, new byte[] {1});
        when(policy.selfieLimit()).thenReturn(new SelfieLimit(0));
        assertThatThrownBy(() -> service.result(1, "job-1"))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SELFIE_LIMIT));
        assertThat(service.current(1)).isEmpty();
        assertThat(service.status(1, "job-1").error().code()).isEqualTo("SELFIE_LIMIT");
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
        assertThat(avatars.findOrCreate(1).face()).isEmpty();
        when(policy.selfieLimit()).thenReturn(new SelfieLimit(1));
        when(gateway.submit(eq(1L), any()))
                .thenReturn(new FaceJob("job-2", FaceJob.Status.QUEUED, null, null));
        assertThat(service.submit(1, new byte[] {2}).jobId()).isEqualTo("job-2");
    }

    @Test
    void uncertainSubmissionCanRetrySamePhotoAfterLease() {
        when(gateway.submit(eq(1L), any())).thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(FaceJobRejectedException.class);
        when(gateway.submit(eq(1L), any())).thenReturn(queued);
        assertThat(service(Clock.offset(clock, Duration.ofSeconds(31))).submit(1, new byte[] {1}))
                .isEqualTo(queued);
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void expiredUnknownSubmissionAllowsRetakingPhoto() {
        when(gateway.submit(eq(1L), any())).thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(FaceJobRejectedException.class);
        when(gateway.submit(eq(1L), any())).thenReturn(queued);
        assertThat(service(Clock.offset(clock, Duration.ofSeconds(31))).submit(1, new byte[] {2}))
                .isEqualTo(queued);
    }

    @Test
    void lostRemoteJobCanBeSubmittedAgain() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1"))
                .thenThrow(new FaceJobRejectedException(404, "JOB_NOT_FOUND"));
        assertThatThrownBy(() -> service.status(1, "job-1"))
                .isInstanceOf(FaceJobRejectedException.class);
        when(gateway.submit(eq(1L), any()))
                .thenReturn(new FaceJob("job-2", FaceJob.Status.QUEUED, null, null));
        assertThat(service.submit(1, new byte[] {1}).jobId()).isEqualTo("job-2");
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void featureConsentAndFailureLimitRejectBeforeAiCall() {
        when(policy.features()).thenReturn(new FeatureToggles(false, true));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(BusinessRuleException.class);
        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        when(members.hasConsent(1, ConsentItem.FACE_TRANSFER)).thenReturn(false);
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(BusinessRuleException.class);
        when(members.hasConsent(1, ConsentItem.FACE_TRANSFER)).thenReturn(true);
        for (int index = 0; index < 5; index++) failures.recordFailure(1, LocalDate.now(clock));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SELFIE_RETRY_LIMIT));
        verifyNoInteractions(gateway);
    }

    @Test
    void memberStateIsReadAfterAvatarLock() {
        AvatarRepository repository = mock(AvatarRepository.class);
        when(repository.findForUpdate(1)).thenReturn(Optional.of(Avatar.create(1)));
        when(members.isActive(1)).thenReturn(false);
        SelfieJobService locked =
                new SelfieJobService(
                        gateway,
                        jobs,
                        repository,
                        members,
                        policy,
                        failures,
                        AvatarFakes.transactions(),
                        clock);
        assertThatThrownBy(() -> locked.submit(1, new byte[] {1}))
                .isInstanceOf(com.a207.runkk.common.PermissionDeniedException.class);
        var order = inOrder(repository, members);
        order.verify(repository).findForUpdate(1);
        order.verify(members).isActive(1);
        verifyNoInteractions(gateway);
    }

    @Test
    void withdrawalDeletesLocalJobHistory() {
        service.submit(1, new byte[] {1});
        service.onMemberWithdrawn(new MemberWithdrawn(1, LocalDateTime.now(clock)));
        assertThatThrownBy(() -> service.status(1, "job-1"))
                .isInstanceOf(FaceJobRejectedException.class);
        assertThat(jobs.rows).isEmpty();
    }

    static class MemoryJobs implements SelfieJobRepository {
        final Map<String, SelfieJob> rows = new HashMap<>();

        public Optional<SelfieJob> find(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        public Optional<SelfieJob> byJob(long memberId, String jobId) {
            return rows.values().stream()
                    .filter(j -> j.memberId() == memberId && Objects.equals(j.jobId(), jobId))
                    .findFirst();
        }

        public Optional<SelfieJob> pending(long memberId) {
            return rows.values().stream()
                    .filter(j -> j.memberId() == memberId && !j.finished())
                    .findFirst();
        }

        public void save(SelfieJob job) {
            rows.put(job.requestId(), job);
        }

        public void deleteByMember(long id) {
            rows.values().removeIf(j -> j.memberId() == id);
        }
    }
}
