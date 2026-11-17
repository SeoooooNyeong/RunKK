package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.DisclosureLevel;
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
        when(gateway.submit(eq(1L), any(), anyInt())).thenReturn(queued);
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
        verify(gateway, times(1)).submit(eq(1L), any(), anyInt());
    }

    @Test
    void differentPhotoIsBlockedWhileOneIsPending() {
        service.submit(1, new byte[] {1});
        assertThatThrownBy(() -> service.submit(1, new byte[] {2}))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SELFIE_IN_PROGRESS));
        verify(gateway, times(1)).submit(eq(1L), any(), anyInt());
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

    private void finishJob() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        service.status(1, "job-1");
        service.result(1, "job-1");
    }

    private static GeneratedHead w9Head() {
        return new GeneratedHead(
                "head-1",
                "1",
                "face",
                "eyes",
                "hair",
                Map.of("hair", "short"),
                "short",
                Map.of("pivotX", 0.5),
                Map.of("renderFaceImage", "render", "neckMask", "neck", "skinColor", "#f0c8a0"),
                "now",
                "front",
                "right",
                "right-face",
                "right-eyes",
                "right-hair",
                Map.of("scale", 1.0));
    }

    @Test
    void headForViewersDropsOriginalsTheAppDoesNotUse() {
        // S15P31A207-300 핫픽스: 앱이 쓰지 않는 정면·옆 원본과 renderFaceImage를 빼서 응답을 줄인다
        finishJob();
        when(gateway.result(1, "job-1")).thenReturn(w9Head());
        when(members.disclosureOf(2, List.of(1L))).thenReturn(Map.of(1L, DisclosureLevel.PUBLIC));
        GeneratedHead viewed = service.head(2, 1, "job-1");
        assertThat(viewed.frontImage()).isNull();
        assertThat(viewed.rightImage()).isNull();
        assertThat(viewed.mobile())
                .doesNotContainKey("renderFaceImage")
                .containsEntry("neckMask", "neck")
                .containsEntry("skinColor", "#f0c8a0");
        assertThat(viewed.faceImage()).isEqualTo("face");
        assertThat(viewed.eyesImage()).isEqualTo("eyes");
        assertThat(viewed.hairImage()).isEqualTo("hair");
        assertThat(viewed.rightFaceImage()).isEqualTo("right-face");
        assertThat(viewed.rightEyesImage()).isEqualTo("right-eyes");
        assertThat(viewed.rightHairImage()).isEqualTo("right-hair");
        assertThat(viewed.rightLayerLayout()).containsEntry("scale", 1.0);
        assertThat(viewed.headId()).isEqualTo("head-1");
        assertThat(viewed.traits()).containsEntry("hair", "short");
        assertThat(viewed.alignment()).containsEntry("pivotX", 0.5);
        // 셀카 작업 결과(본인 조회)는 그대로 둔다
        assertThat(service.result(1, "job-1").frontImage()).isEqualTo("front");
    }

    @Test
    void headVisibilityIsCheckedWithoutCallingAi() {
        finishJob();
        clearInvocations(gateway);
        when(members.disclosureOf(2, List.of(1L))).thenReturn(Map.of(1L, DisclosureLevel.PUBLIC));
        service.requireVisibleHead(2, 1, "job-1");
        when(members.disclosureOf(3, List.of(1L)))
                .thenReturn(Map.of(1L, DisclosureLevel.WITHDRAWN));
        assertThatThrownBy(() -> service.requireVisibleHead(3, 1, "job-1"))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.requireVisibleHead(2, 1, "job-x"))
                .isInstanceOfSatisfying(
                        FaceJobRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo("JOB_NOT_FOUND"));
        verifyNoInteractions(gateway);
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
        when(gateway.submit(eq(1L), any(), anyInt()))
                .thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
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
        when(gateway.submit(eq(1L), any(), anyInt()))
                .thenReturn(new FaceJob("job-2", FaceJob.Status.QUEUED, null, null));
        assertThat(service.submit(1, new byte[] {2}).jobId()).isEqualTo("job-2");
    }

    @Test
    void uncertainSubmissionCanRetrySamePhotoAfterLease() {
        when(gateway.submit(eq(1L), any(), anyInt()))
                .thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(FaceJobRejectedException.class);
        when(gateway.submit(eq(1L), any(), anyInt())).thenReturn(queued);
        assertThat(service(Clock.offset(clock, Duration.ofSeconds(31))).submit(1, new byte[] {1}))
                .isEqualTo(queued);
        assertThat(avatars.findOrCreate(1).creationCount()).isZero();
    }

    @Test
    void expiredUnknownSubmissionAllowsRetakingPhoto() {
        when(gateway.submit(eq(1L), any(), anyInt()))
                .thenThrow(new FaceJobRejectedException(504, "TIMEOUT"));
        assertThatThrownBy(() -> service.submit(1, new byte[] {1}))
                .isInstanceOf(FaceJobRejectedException.class);
        when(gateway.submit(eq(1L), any(), anyInt())).thenReturn(queued);
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
        when(gateway.submit(eq(1L), any(), anyInt()))
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

    private static FaceJob failed(String jobId, String code) {
        return new FaceJob(jobId, FaceJob.Status.FAILED, null, new FaceJob.Error(code, "실패"));
    }

    @Test
    void unknownOutcomeIsRecoveredWhenAiLaterReportsSuccess() {
        // DV-9: Node가 결과를 확정하지 못하고 실패로 끝냈지만 그 뒤 머리를 저장해 작업을 성공으로 회수했다
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "GENERATION_OUTCOME_UNKNOWN"));
        assertThat(service.status(1, "job-1").status()).isEqualTo(FaceJob.Status.FAILED);
        assertThat(service.current(1)).isEmpty();
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        assertThat(service.status(1, "job-1").status()).isEqualTo(FaceJob.Status.SUCCEEDED);
        assertThat(service.current(1).orElseThrow().jobId()).isEqualTo("job-1");
        service.result(1, "job-1");
        assertThat(avatars.findOrCreate(1).creationCount()).isEqualTo(1);
        assertThat(avatars.findOrCreate(1).face().orElseThrow().renderKey())
                .isEqualTo("head:1:job-1");
        verify(gateway, times(1)).submit(eq(1L), any(), anyInt());
    }

    @Test
    void definiteFailureIsNotAskedAgain() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "NO_FACE_DETECTED"));
        service.status(1, "job-1");
        service.status(1, "job-1");
        verify(gateway, times(1)).status(1, "job-1");
    }

    @Test
    void unknownOutcomeIsNotAskedAfterTheRecoveryWindow() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "UPSTREAM_TIMEOUT"));
        service.status(1, "job-1");
        service(Clock.offset(clock, Duration.ofMinutes(61))).status(1, "job-1");
        verify(gateway, times(1)).status(1, "job-1");
    }

    @Test
    void recoveredHeadIsNotAppliedOverDefaultChosenAfterFailure() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "GENERATION_OUTCOME_UNKNOWN"));
        service.status(1, "job-1");
        avatars.findForUpdate(1).orElseThrow().chooseDefault(DefaultCharacter.FOX);
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        assertThat(service.status(1, "job-1").status()).isEqualTo(FaceJob.Status.FAILED);
        assertThat(avatars.findOrCreate(1).face().orElseThrow().renderKey())
                .isEqualTo("default:FOX");
    }

    @Test
    void retryingSamePhotoAfterUnknownFailureMakesNewAttemptOnce() {
        // 같은 사진 재전송은 같은 작업을 돌려준다(멱등). 실패가 확정된 뒤 다시 고르면 새 시도 번호로 새 작업을 만든다
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "GENERATION_OUTCOME_UNKNOWN"));
        service.status(1, "job-1");
        FaceJob second = new FaceJob("job-2", FaceJob.Status.QUEUED, null, null);
        when(gateway.submit(eq(1L), any(), eq(2))).thenReturn(second);
        when(gateway.status(1, "job-2")).thenReturn(second);
        assertThat(service.submit(1, new byte[] {1}).jobId()).isEqualTo("job-2");
        assertThat(service.submit(1, new byte[] {1}).jobId()).isEqualTo("job-2");
        verify(gateway, times(1)).submit(eq(1L), any(), eq(1));
        verify(gateway, times(1)).submit(eq(1L), any(), eq(2));
        assertThat(service.current(1).orElseThrow().jobId()).isEqualTo("job-2");
    }

    @Test
    void retryingSamePhotoReturnsRecoveredJobInsteadOfPayingAgain() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "UPSTREAM_TIMEOUT"));
        service.status(1, "job-1");
        when(gateway.status(1, "job-1"))
                .thenReturn(new FaceJob("job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        assertThat(service.submit(1, new byte[] {1}).status()).isEqualTo(FaceJob.Status.SUCCEEDED);
        verify(gateway, times(1)).submit(eq(1L), any(), anyInt());
    }

    @Test
    void samePhotoRejectedForQualityIsNotRegenerated() {
        service.submit(1, new byte[] {1});
        when(gateway.status(1, "job-1")).thenReturn(failed("job-1", "SELFIE_QUALITY_REJECTED"));
        service.status(1, "job-1");
        FaceJob again = service.submit(1, new byte[] {1});
        assertThat(again.jobId()).isEqualTo("job-1");
        assertThat(again.error().code()).isEqualTo("SELFIE_QUALITY_REJECTED");
        verify(gateway, times(1)).submit(eq(1L), any(), anyInt());
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
