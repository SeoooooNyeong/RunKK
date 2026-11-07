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
    void missingBirthYear_failsWithoutCallingAi() {
        when(requests.create(ID, 42)).thenThrow(new RunBodyRejectedException("BIRTH_YEAR_MISSING"));
        saveAndSubmit();
        assertThat(job().status()).isEqualTo(RunBodyStatus.FAILED);
        verify(gateway, never()).submit(any());
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
