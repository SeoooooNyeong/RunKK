package com.a207.runkk.runbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 05-19 3.3 RunBodyJob 상태도의 전이 전부(7절 단위). */
@DisplayName("런바디 작업 상태 전이")
class RunBodyJobTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 7, 7, 0);
    private static final String ID = "00000000-0000-4000-8000-000000000001";

    private static RunBodyJob analyzing() {
        RunBodyJob job = RunBodyJob.pending(ID, 42, T0);
        job.submitted(T0);
        return job;
    }

    @Test
    void pending_waitsGraceBeforePollDueResubmits() {
        RunBodyJob job = RunBodyJob.pending(ID, 42, T0);
        assertThat(job.status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job.attempts()).isZero();
        assertThat(job.duePoll(T0.plusSeconds(59))).isFalse();
        assertThat(job.duePoll(T0.plusMinutes(1))).isTrue();
        assertThat(job.ownedBy(42)).isTrue();
        assertThat(job.ownedBy(43)).isFalse();
    }

    @Test
    void pending_rejectsBadInput() {
        assertThatThrownBy(() -> RunBodyJob.pending("", 42, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunBodyJob.pending(ID, 0, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunBodyJob.pending(ID + "x", 42, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitted_pollsAfterFiveSeconds() {
        RunBodyJob job = analyzing();
        assertThat(job.status()).isEqualTo(RunBodyStatus.ANALYZING);
        assertThat(job.nextPollAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(job.duePoll(T0.plusSeconds(4))).isFalse();
        assertThat(job.duePoll(T0.plusSeconds(5))).isTrue();
    }

    @Test
    void stillAnalyzing_backsOff5_10_20_thenStays20() {
        RunBodyJob job = analyzing();
        LocalDateTime now = T0.plusSeconds(5);
        job.stillAnalyzing(now);
        assertThat(job.nextPollAt()).isEqualTo(now.plusSeconds(10));
        job.stillAnalyzing(now);
        assertThat(job.nextPollAt()).isEqualTo(now.plusSeconds(20));
        job.stillAnalyzing(now);
        assertThat(job.nextPollAt()).isEqualTo(now.plusSeconds(20));
        assertThat(job.status()).isEqualTo(RunBodyStatus.ANALYZING);
        assertThat(job.attempts()).isZero();
    }

    @Test
    void stillAnalyzing_pastFiveMinutes_countsAsFailure() {
        RunBodyJob job = analyzing();
        job.stillAnalyzing(T0.plusMinutes(5));
        assertThat(job.status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job.attempts()).isEqualTo(1);
    }

    @Test
    void done_fromAnalyzingOrPending_closesJob() {
        RunBodyJob job = analyzing();
        job.done();
        assertThat(job.status()).isEqualTo(RunBodyStatus.DONE);
        assertThat(job.duePoll(T0.plusDays(1))).isFalse();

        RunBodyJob resubmitted = RunBodyJob.pending(ID, 42, T0);
        resubmitted.done();
        assertThat(resubmitted.status()).isEqualTo(RunBodyStatus.DONE);
    }

    @Test
    void failed_twiceReturnsToPending_thirdTimeFails() {
        RunBodyJob job = analyzing();
        job.failed(T0.plusSeconds(5));
        assertThat(job.status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job.nextPollAt()).isEqualTo(T0.plusSeconds(35));
        job.submitted(T0.plusSeconds(35));
        job.failed(T0.plusSeconds(40));
        assertThat(job.status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job.attempts()).isEqualTo(2);
        job.failed(T0.plusSeconds(70));
        assertThat(job.status()).isEqualTo(RunBodyStatus.FAILED);
        assertThat(job.attempts()).isEqualTo(3);
        assertThat(job.duePoll(T0.plusDays(1))).isFalse();
        job.failed(T0.plusSeconds(80));
        assertThat(job.attempts()).as("닫힌 작업은 더 세지 않는다").isEqualTo(3);
    }

    @Test
    void rejected_failsAtOnce_butKeepsDone() {
        RunBodyJob job = RunBodyJob.pending(ID, 42, T0);
        job.rejected();
        assertThat(job.status()).isEqualTo(RunBodyStatus.FAILED);
        assertThat(job.attempts()).isZero();

        RunBodyJob done = analyzing();
        done.done();
        done.rejected();
        assertThat(done.status()).isEqualTo(RunBodyStatus.DONE);
    }

    @Test
    void unreachable_doesNotCount_andGivesUpAfterOneDay() {
        RunBodyJob job = RunBodyJob.pending(ID, 42, T0);
        job.unreachable(T0.plusMinutes(1));
        assertThat(job.attempts()).isZero();
        assertThat(job.status()).isEqualTo(RunBodyStatus.PENDING);
        assertThat(job.nextPollAt()).isEqualTo(T0.plusMinutes(2));
        job.unreachable(T0.plusDays(1));
        assertThat(job.status()).isEqualTo(RunBodyStatus.FAILED);
        assertThat(job.attempts()).isZero();
    }

    @Test
    void wrongOrder_isRejected() {
        RunBodyJob job = analyzing();
        assertThatThrownBy(() -> job.submitted(T0)).isInstanceOf(IllegalStateException.class);
        RunBodyJob pending = RunBodyJob.pending(ID, 42, T0);
        assertThatThrownBy(() -> pending.stillAnalyzing(T0))
                .isInstanceOf(IllegalStateException.class);
        RunBodyJob failed = RunBodyJob.pending(ID, 42, T0);
        failed.rejected();
        assertThatThrownBy(failed::done).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void g30_scored_onlyWhenDone_withinRange() {
        RunBodyJob job = analyzing();
        assertThatThrownBy(() -> job.scored(70)).isInstanceOf(IllegalStateException.class);
        job.done();
        job.scored(null);
        assertThat(job.score()).as("점수 있는 축이 없으면 남기지 않는다").isNull();
        job.scored(78);
        assertThat(job.score()).isEqualTo(78);
        assertThatThrownBy(() -> job.scored(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job.scored(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(job.score()).isEqualTo(78);
    }
}
