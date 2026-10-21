package com.a207.runkk.batch.analysis;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * batch.analysis 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39): 0시 랭킹 재집계, 20시 계획 미이행 알림, 3시 지난
 * 계획·목표 정리(한국 시간).
 */
@Configuration
@EnableScheduling
public class AnalysisSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRebuildJob rankingRebuildJob;
    private final PlanReminderJob planReminderJob;
    private final PlanPurgeJob planPurgeJob;
    private final String rankingCron;
    private final String reminderCron;
    private final String purgeCron;

    public AnalysisSchedulingConfig(
            RankingRebuildJob rankingRebuildJob,
            PlanReminderJob planReminderJob,
            PlanPurgeJob planPurgeJob,
            @Value("${runkk.batch.ranking-rebuild-cron}") String rankingCron,
            @Value("${runkk.batch.plan-reminder-cron}") String reminderCron,
            @Value("${runkk.batch.plan-purge-cron}") String purgeCron) {
        this.rankingRebuildJob = rankingRebuildJob;
        this.planReminderJob = planReminderJob;
        this.planPurgeJob = planPurgeJob;
        this.rankingCron = rankingCron;
        this.reminderCron = reminderCron;
        this.purgeCron = purgeCron;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(rankingRebuildJob::run, new CronTrigger(rankingCron, SEOUL)));
        registrar.addCronTask(
                new CronTask(planReminderJob::run, new CronTrigger(reminderCron, SEOUL)));
        registrar.addCronTask(new CronTask(planPurgeJob::run, new CronTrigger(purgeCron, SEOUL)));
    }
}
