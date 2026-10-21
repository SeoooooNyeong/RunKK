package com.a207.runkk.batch.analysis;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(AnalysisSchedulingConfig.Schedule.class)
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
            Schedule schedule) {
        this.rankingRebuildJob = rankingRebuildJob;
        this.planReminderJob = planReminderJob;
        this.planPurgeJob = planPurgeJob;
        this.rankingCron = schedule.rankingRebuildCron();
        this.reminderCron = schedule.planReminderCron();
        this.purgeCron = schedule.planPurgeCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(rankingRebuildJob::run, new CronTrigger(rankingCron, SEOUL)));
        registrar.addCronTask(
                new CronTask(planReminderJob::run, new CronTrigger(reminderCron, SEOUL)));
        registrar.addCronTask(new CronTask(planPurgeJob::run, new CronTrigger(purgeCron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param rankingRebuildCron runkk.batch.ranking-rebuild-cron
     * @param planReminderCron runkk.batch.plan-reminder-cron
     * @param planPurgeCron runkk.batch.plan-purge-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(
            String rankingRebuildCron, String planReminderCron, String planPurgeCron) {}
}
