package com.a207.runkk.runbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.BirthYearSet;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 05-19 S-31·D-440·D-443·D-445·D-450 RunBodyService(가짜 저장소·게이트웨이). */
@DisplayName("런바디 작업 진행과 조회")
class RunBodyServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 7, 7, 30);
    private static final String ID = "00000000-0000-4000-8000-000000000042";
    private static final RunBodyRequest REQUEST =
            new RunBodyRequest(
                    ID,
                    "42",
                    "phone",
                    T0.atOffset(ZoneOffset.ofHours(9)),
                    T0.plusMinutes(30).atOffset(ZoneOffset.ofHours(9)),
                    1_800_000,
                    5_000,
                    1995,
                    List.of(new RunBodyRequest.Sample(0, 150, null, null)),
                    List.of(),
                    List.of());

    private final MemoryJobs jobs = new MemoryJobs();
    private final RunBodyGateway gateway = mock(RunBodyGateway.class);
    private final RunBodyRequestFactory requests = mock(RunBodyRequestFactory.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final MovableClock clock = new MovableClock(T0);
    private RunBodyService service;

    @BeforeEach
    void setUp() {
        enable(true);
        when(requests.create(ID, 42)).thenReturn(REQUEST);
        service =
                new RunBodyService(
                        jobs, gateway, requests, policy, clock, NoTransactions.manager());
    }

    private void enable(boolean on) {
        when(policy.features()).thenReturn(new FeatureToggles(true, true, false, false, on));
    }

    private static RunRecordSaved saved(boolean valid) {
        return new RunRecordSaved(
                ID,
                42,
                T0,
                Distance.ofMeters(5_000),
                Duration.ofMinutes(30),
                valid,
                valid ? 50.0 : null,
                RunMode.FREE,
                null,
                null,
                true,
                valid ? 1 : 0,
                T0.plusMinutes(31));
    }

    private RunBodyJob job() {
        return jobs.rows.get(ID);
    }

    private void saveAndSubmit() {
        service.onRunRecordSaved(saved(true));
        service.submitAfterCommit(saved(true));
    }

    @Test
    @DisplayName("e2e 3: 작업은 러닝 시작 일시를 지닌다(점수 변화·목록 순서 기준)")
    void savedRun_keepsRunStartedAt() {
        service.onRunRecordSaved(saved(true));
        assertThat(job().runStartedAt()).isEqualTo(T0);
        assertThat(job().createdAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("e2e 3: 늦게 올린 예전 러닝은 시작 일시 순서 자리에 놓이고 점수 변화도 그 순서로 센다")
    void g30_sessionsOf_ordersByRunStart_notUploadOrder() {
        // 올린 순서: a(7:00 시작, 70) → late(6:00 시작, 60, 나중에 올림) → c(8:00 시작, 76)
        jobs.save(doneJob("a", 0, 70, 42, 0));
        jobs.save(doneJob("late", 10, 60, 42, -60));
        jobs.save(doneJob("c", 20, 76, 42, 60));

        assertThat(service.sessionsOf(42, null, 10).items())
                .containsExactly(
                        new RunBodySessionItem("c", RunBodyStatus.DONE, 76, 6),
                        new RunBodySessionItem("a", RunBodyStatus.DONE, 70, 10),
                        new RunBodySessionItem("late", RunBodyStatus.DONE, 60, null));
        RunBodySessionPage first = service.sessionsOf(42, null, 1);
        assertThat(first.items())
                .containsExactly(new RunBodySessionItem("c", RunBodyStatus.DONE, 76, 6));
        assertThat(service.sessionsOf(42, first.nextCursor(), 1).items())
                .containsExactly(new RunBodySessionItem("a", RunBodyStatus.DONE, 70, 10));
    }

    @Test
    void s31_savedValidRun_isSubmittedAfterCommit_thenPolledUntilDone() {
        when(gateway.statusOf(ID))
                .thenReturn(RunBodyStatus.ANALYZING)
                .thenReturn(RunBodyStatus.DONE);

        service.onRunRecordSaved(saved(true));
        assertThat(job().status()).isEqualTo(RunBodyStatus.PENDING);
        service.submitAfterCommit(saved(true));
        verify(gateway).submit(REQUEST);
        assertThat(job().status()).isEqualTo(RunBodyStatus.ANALYZING);

        service.pollDue();
        verify(gateway, never()).statusOf(ID);
        clock.plus(Duration.ofSeconds(5));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.ANALYZING);
        clock.plus(Duration.ofSeconds(10));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.DONE);
        verify(gateway, times(2)).statusOf(ID);
    }

    @Test
    void d445_excludedRun_getsNoJob() {
        service.onRunRecordSaved(saved(false));
        service.submitAfterCommit(saved(false));
        assertThat(jobs.rows).isEmpty();
        verify(gateway, never()).submit(any());
    }

    @Test
    void d440_disabled_createsNothing_pollsNothing_andLookupsAreClosed() {
        enable(false);
        service.onRunRecordSaved(saved(true));
        service.pollDue();
        assertThat(jobs.rows).isEmpty();
        assertThatThrownBy(() -> service.sessionOf(42, ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.FEATURE_CLOSED);
        assertThatThrownBy(() -> service.monthOf(42, YearMonth.of(2026, 11)))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.FEATURE_CLOSED);
    }

    @Test
    void lostAfterCommitSubmit_isResubmittedByPollDueAfterGrace() {
        service.onRunRecordSaved(saved(true));
        service.pollDue();
        verify(gateway, never()).submit(any());
        clock.plus(Duration.ofMinutes(1));
        service.pollDue();
        verify(gateway).submit(REQUEST);
        assertThat(job().status()).isEqualTo(RunBodyStatus.ANALYZING);
    }

    @Test
    void d450_tooLarge_failsAtOnce_withoutRetry() {
        doThrow(new RunBodyRejectedException("TOO_LARGE")).when(gateway).submit(REQUEST);
        saveAndSubmit();
        assertThat(job().status()).isEqualTo(RunBodyStatus.FAILED);
        clock.plus(Duration.ofHours(1));
        service.pollDue();
        verify(gateway, times(1)).submit(REQUEST);
    }

    @Test
    void d443_aiUnavailable_resubmitsUpToThreeTimes() {
        doThrow(new RunBodyUnavailableException("STATUS_503", false)).when(gateway).submit(REQUEST);
        saveAndSubmit();
        assertThat(job().attempts()).isEqualTo(1);
        clock.plus(Duration.ofSeconds(30));
        service.pollDue();
        clock.plus(Duration.ofSeconds(30));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.FAILED);
        assertThat(job().attempts()).isEqualTo(3);
        clock.plus(Duration.ofMinutes(5));
        service.pollDue();
        verify(gateway, times(3)).submit(REQUEST);
    }

    @Test
    void d450_connectionFailure_isNotCounted() {
        doThrow(new RunBodyUnavailableException("CONNECTION_FAILED", true))
                .doNothing()
                .when(gateway)
                .submit(REQUEST);
        saveAndSubmit();
        assertThat(job().attempts()).isZero();
        assertThat(job().status()).isEqualTo(RunBodyStatus.PENDING);
        clock.plus(Duration.ofMinutes(1));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.ANALYZING);
        assertThat(job().attempts()).isZero();
    }

    @Test
    void aiAnalysisFailed_resubmits() {
        when(gateway.statusOf(ID)).thenReturn(RunBodyStatus.FAILED);
        saveAndSubmit();
        clock.plus(Duration.ofSeconds(5));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job().attempts()).isEqualTo(1);
        clock.plus(Duration.ofSeconds(30));
        service.pollDue();
        verify(gateway, times(2)).submit(REQUEST);
    }

    @Test
    @DisplayName("e2e 1: 출생연도가 없으면 ai를 부르지 않고 사유를 남기며, 그 회원이 출생연도를 넣으면 다시 제출한다")
    void missingBirthYear_keepsReason_andResubmitsAfterBirthYearSet() {
        when(requests.create(ID, 42))
                .thenThrow(new RunBodyRejectedException("BIRTH_YEAR_MISSING"))
                .thenReturn(REQUEST);
        saveAndSubmit();
        assertThat(job().status()).isEqualTo(RunBodyStatus.FAILED);
        verify(gateway, never()).submit(any());
        assertThat(service.sessionOf(42, ID))
                .isEqualTo(
                        new RunBodySessionView(
                                ID, RunBodyStatus.FAILED, null, "BIRTH_YEAR_MISSING"));

        service.onBirthYearSet(new BirthYearSet(43, T0.plusMinutes(40)));
        assertThat(job().status()).as("다른 회원").isEqualTo(RunBodyStatus.FAILED);
        service.onBirthYearSet(new BirthYearSet(42, T0.plusMinutes(40)));
        assertThat(job().status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(service.sessionOf(42, ID).failureReason()).isNull();

        service.pollDue();
        verify(gateway).submit(REQUEST);
        assertThat(job().status()).isEqualTo(RunBodyStatus.ANALYZING);
    }

    @Test
    void fr266_sessionOf_mapsJobAndAiStates() {
        assertThatThrownBy(() -> service.sessionOf(42, ID))
                .isInstanceOf(NotFoundRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RUNBODY_NOT_FOUND);

        service.onRunRecordSaved(saved(true));
        assertThat(service.sessionOf(42, ID))
                .isEqualTo(new RunBodySessionView(ID, RunBodyStatus.PENDING, null));
        assertThatThrownBy(() -> service.sessionOf(43, ID))
                .as("남의 기록")
                .isInstanceOf(NotFoundRuleException.class);

        job().submitted(T0);
        job().done();
        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.done("42", "{\"sessionId\":\"x\"}"));
        assertThat(service.sessionOf(42, ID))
                .isEqualTo(new RunBodySessionView(ID, RunBodyStatus.DONE, "{\"sessionId\":\"x\"}"));

        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.notReady(RunBodyStatus.ANALYZING));
        assertThat(service.sessionOf(42, ID).status()).isEqualTo(RunBodyStatus.ANALYZING);
        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.notReady(RunBodyStatus.FAILED));
        assertThat(service.sessionOf(42, ID).status()).isEqualTo(RunBodyStatus.FAILED);

        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.done("7", "{}"));
        assertThat(service.sessionOf(42, ID))
                .as("ai 결과의 userId가 다르면 남의 결과를 내주지 않는다")
                .isEqualTo(new RunBodySessionView(ID, RunBodyStatus.FAILED, null));

        when(gateway.sessionOf(ID))
                .thenThrow(new RunBodyUnavailableException("TIMEOUT_OR_IO", false));
        assertThatThrownBy(() -> service.sessionOf(42, ID))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RUNBODY_UNAVAILABLE);
    }

    @Test
    void fr267_monthOf_passesAiBody_andMapsFailure() {
        YearMonth month = YearMonth.of(2026, 10);
        when(gateway.monthOf(42, month)).thenReturn(new RunBodyMonth("42", "{\"sessionCount\":0}"));
        assertThat(service.monthOf(42, month).body()).isEqualTo("{\"sessionCount\":0}");

        when(gateway.monthOf(42, month)).thenReturn(new RunBodyMonth("7", "{}"));
        assertThatThrownBy(() -> service.monthOf(42, month))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RUNBODY_UNAVAILABLE);

        when(gateway.monthOf(42, month))
                .thenThrow(new RunBodyUnavailableException("STATUS_503", false));
        assertThatThrownBy(() -> service.monthOf(42, month))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RUNBODY_UNAVAILABLE);
    }

    @Test
    void d448_withdrawalAndRecordDeletion_removeJobs() {
        service.onRunRecordSaved(saved(true));
        service.onRunRecordRemoved(ID);
        assertThat(jobs.rows).isEmpty();

        service.onRunRecordSaved(saved(true));
        new RunBodyWithdrawalListener(jobs)
                .onMemberWithdrawn(new com.a207.runkk.member.MemberWithdrawn(42, T0));
        assertThat(jobs.rows).isEmpty();
    }

    @Test
    void prepareFailure_doesNotBreakRunSave() {
        when(policy.features()).thenThrow(new IllegalStateException("db down"));
        service.onRunRecordSaved(saved(true));
        assertThat(jobs.rows).isEmpty();
    }

    @Test
    void g30_doneJob_keepsOverallScoreFromAiResult() {
        when(gateway.statusOf(ID)).thenReturn(RunBodyStatus.DONE);
        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.done("42", "{}", 78));
        saveAndSubmit();
        clock.plus(Duration.ofSeconds(5));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.DONE);
        assertThat(job().score()).isEqualTo(78);
    }

    @Test
    void g30_scoreReadFailure_keepsDone_andFirstLookupFillsScore() {
        when(gateway.statusOf(ID)).thenReturn(RunBodyStatus.DONE);
        when(gateway.sessionOf(ID))
                .thenThrow(new RunBodyUnavailableException("TIMEOUT_OR_IO", false))
                .thenReturn(RunBodySession.done("42", "{\"axes\":{}}", 81));
        saveAndSubmit();
        clock.plus(Duration.ofSeconds(5));
        service.pollDue();
        assertThat(job().status()).isEqualTo(RunBodyStatus.DONE);
        assertThat(job().score()).isNull();

        assertThat(service.sessionOf(42, ID).status()).isEqualTo(RunBodyStatus.DONE);
        assertThat(job().score()).isEqualTo(81);
    }

    @Test
    void g30_othersResult_isNotScored() {
        when(gateway.statusOf(ID)).thenReturn(RunBodyStatus.DONE);
        when(gateway.sessionOf(ID)).thenReturn(RunBodySession.done("7", "{}", 90));
        saveAndSubmit();
        clock.plus(Duration.ofSeconds(5));
        service.pollDue();
        assertThat(job().score()).isNull();
    }

    @Test
    void g30_sessionsOf_newestFirst_withChangeAgainstPreviousScoredRun_andCursor() {
        // 저장 순서: a(70) → b(실패) → c(76) → d(분석 중) → e(73)
        jobs.save(doneJob("a", 0, 70));
        jobs.save(failedJob("b", 1));
        jobs.save(doneJob("c", 2, 76));
        RunBodyJob d = RunBodyJob.pending("d", 42, T0.plusMinutes(3));
        d.submitted(T0.plusMinutes(3));
        jobs.save(d);
        jobs.save(doneJob("e", 4, 73));
        jobs.save(doneJob("other", 5, 99, 43));

        RunBodySessionPage first = service.sessionsOf(42, null, 2);
        assertThat(first.items())
                .containsExactly(
                        new RunBodySessionItem("e", RunBodyStatus.DONE, 73, -3),
                        new RunBodySessionItem("d", RunBodyStatus.ANALYZING, null, null));
        assertThat(first.nextCursor()).isEqualTo("d");

        RunBodySessionPage second = service.sessionsOf(42, first.nextCursor(), 2);
        assertThat(second.items())
                .containsExactly(
                        new RunBodySessionItem("c", RunBodyStatus.DONE, 76, 6),
                        new RunBodySessionItem("b", RunBodyStatus.FAILED, null, null));
        RunBodySessionPage last = service.sessionsOf(42, second.nextCursor(), 2);
        assertThat(last.items())
                .containsExactly(new RunBodySessionItem("a", RunBodyStatus.DONE, 70, null));
        assertThat(last.nextCursor()).isNull();

        assertThatThrownBy(() -> service.sessionsOf(43, "a", 2))
                .as("남의 커서")
                .isInstanceOf(NotFoundRuleException.class);
        assertThatThrownBy(() -> service.sessionsOf(42, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sessionsOf(42, null, 101))
                .isInstanceOf(IllegalArgumentException.class);
        enable(false);
        assertThatThrownBy(() -> service.sessionsOf(42, null, 2))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.FEATURE_CLOSED);
    }

    private static RunBodyJob doneJob(String id, int minute, int score) {
        return doneJob(id, minute, score, 42);
    }

    private static RunBodyJob doneJob(String id, int minute, int score, long member) {
        return doneJob(id, minute, score, member, minute);
    }

    /** minute에 만들고 startMinute에 시작한 러닝의 끝난 작업. */
    private static RunBodyJob doneJob(
            String id, int minute, int score, long member, int startMinute) {
        RunBodyJob job =
                RunBodyJob.pending(id, member, T0.plusMinutes(minute), T0.plusMinutes(startMinute));
        job.submitted(T0.plusMinutes(minute));
        job.done();
        job.scored(score);
        return job;
    }

    private static RunBodyJob failedJob(String id, int minute) {
        RunBodyJob job = RunBodyJob.pending(id, 42, T0.plusMinutes(minute));
        job.rejected();
        return job;
    }

    /** 메모리 저장소. */
    static final class MemoryJobs implements RunBodyJobRepository {
        final Map<String, RunBodyJob> rows = new HashMap<>();

        @Override
        public Optional<RunBodyJob> find(String recordId) {
            return Optional.ofNullable(rows.get(recordId));
        }

        @Override
        public void save(RunBodyJob job) {
            rows.put(job.recordId(), job);
        }

        @Override
        public List<String> dueRecordIds(LocalDateTime now, int limit) {
            List<RunBodyJob> due = new ArrayList<>();
            for (RunBodyJob job : rows.values()) {
                if (job.duePoll(now)) {
                    due.add(job);
                }
            }
            due.sort(Comparator.comparing(RunBodyJob::nextPollAt));
            return due.stream().limit(limit).map(RunBodyJob::recordId).toList();
        }

        @Override
        public List<RunBodyJob> latestOf(long memberId, RunBodyJob after, int limit) {
            return newestFirst(memberId).stream()
                    .filter(j -> after == null || NEWEST_FIRST.compare(j, after) > 0)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Integer> previousScore(RunBodyJob before) {
            return newestFirst(before.memberId()).stream()
                    .filter(j -> NEWEST_FIRST.compare(j, before) > 0 && j.score() != null)
                    .map(RunBodyJob::score)
                    .findFirst();
        }

        private static final Comparator<RunBodyJob> NEWEST_FIRST =
                Comparator.comparing(RunBodyJob::runStartedAt)
                        .thenComparing(RunBodyJob::recordId)
                        .reversed();

        private List<RunBodyJob> newestFirst(long memberId) {
            return rows.values().stream()
                    .filter(j -> j.memberId() == memberId)
                    .sorted(NEWEST_FIRST)
                    .toList();
        }

        @Override
        public List<RunBodyJob> failedFor(long memberId, String reason) {
            return rows.values().stream()
                    .filter(j -> j.memberId() == memberId)
                    .filter(j -> j.status() == RunBodyStatus.FAILED)
                    .filter(j -> reason.equals(j.failureReason()))
                    .toList();
        }

        @Override
        public void delete(String recordId) {
            rows.remove(recordId);
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.values().removeIf(job -> job.memberId() == memberId);
        }
    }

    /** 시험이 움직이는 시계. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(LocalDateTime start) {
            this.now = start.atZone(SEOUL).toInstant();
        }

        void plus(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
