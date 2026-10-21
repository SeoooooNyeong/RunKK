package com.a207.runkk.batch.analysis;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.analysis 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39): 0시 랭킹 재집계, 20시 계획 미이행 알림(한국 시간). */
@Configuration
@EnableScheduling
public class AnalysisSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRebuildJob rankingRebuildJob;
    private final PlanReminderJob planReminderJob;
    private final String rankingCron;
    private final String reminderCron;

    public AnalysisSchedulingConfig(
            RankingRebuildJob rankingRebuildJob,
            PlanReminderJob planReminderJob,
            @Value("${runkk.batch.ranking-rebuild-cron}") String rankingCron,
            @Value("${runkk.batch.plan-reminder-cron}") String reminderCron) {
        this.rankingRebuildJob = rankingRebuildJob;
        this.planReminderJob = planReminderJob;
        this.rankingCron = rankingCron;
        this.reminderCron = reminderCron;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(rankingRebuildJob::run, new CronTrigger(rankingCron, SEOUL)));
        registrar.addCronTask(
                new CronTask(planReminderJob::run, new CronTrigger(reminderCron, SEOUL)));
    }
}
