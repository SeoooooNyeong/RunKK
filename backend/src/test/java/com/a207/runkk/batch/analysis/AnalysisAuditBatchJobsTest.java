package com.a207.runkk.batch.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.analysis.CoachService;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.batch.audit.AuditPurgeJob;
import com.a207.runkk.batch.audit.AuditSchedulingConfig;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** batch.analysis(0시 랭킹, 20시 미이행 알림)·batch.audit(3시 6개월 정리) 작업(설계 6.1.13, D-39). */
@DisplayName("시각 작업(B5 범위)")
class AnalysisAuditBatchJobsTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Test
    @DisplayName(
            "RankingRebuildJob → rebuild(now), PlanReminderJob → remindMissedPlans(now), 실패는 삼킨다")
    void analysisJobs() {
        RankingService ranking = mock(RankingService.class);
        new RankingRebuildJob(ranking, clock).run();
        verify(ranking).rebuild(NOW);
        doThrow(new IllegalStateException("db")).when(ranking).rebuild(any());
        new RankingRebuildJob(ranking, clock).run();

        CoachService coach = mock(CoachService.class);
        new PlanReminderJob(coach, clock).run();
        verify(coach).remindMissedPlans(NOW);
        when(coach.remindMissedPlans(any())).thenThrow(new IllegalStateException("db"));
        new PlanReminderJob(coach, clock).run();
    }

    @Test
    @DisplayName("AuditPurgeJob → purgeExpired(now), 실패는 삼킨다")
    void auditJob() {
        AuditService audit = mock(AuditService.class);
        new AuditPurgeJob(audit, clock).run();
        verify(audit).purgeExpired(NOW);
        when(audit.purgeExpired(any())).thenThrow(new IllegalStateException("db"));
        new AuditPurgeJob(audit, clock).run();
    }

    @Test
    @DisplayName("스케줄 설정: analysis 0시·20시·3시 30분 cron 3개, audit 3시 cron 1개")
    void schedulingConfigs() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new AnalysisSchedulingConfig(
                        mock(RankingRebuildJob.class),
                        mock(PlanReminderJob.class),
                        mock(PlanPurgeJob.class),
                        "0 0 0 * * *",
                        "0 0 20 * * *",
                        "0 30 3 * * *")
                .configureTasks(registrar);
        new AuditSchedulingConfig(mock(AuditPurgeJob.class), "0 0 3 * * *")
                .configureTasks(registrar);
        assertThat(registrar.getCronTaskList())
                .extracting(t -> t.getExpression())
                .containsExactly("0 0 0 * * *", "0 0 20 * * *", "0 30 3 * * *", "0 0 3 * * *");
    }

    @Test
    @DisplayName("PlanPurgeJob: 오늘 날짜로 지난 계획 정리를 부르고 실패는 로그만")
    void planPurgeJob() {
        com.a207.runkk.analysis.CoachService coach =
                mock(com.a207.runkk.analysis.CoachService.class);
        new PlanPurgeJob(coach, clock).run();
        verify(coach).purgePastPlans(NOW.toLocalDate());
        when(coach.purgePastPlans(any())).thenThrow(new IllegalStateException("db"));
        new PlanPurgeJob(coach, clock).run();
    }
}
