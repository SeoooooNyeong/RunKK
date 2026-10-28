package com.a207.runkk.batch.running;

import com.a207.runkk.common.ServerZone;
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
 * batch.running 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 원자료 파기 매일 3시, 티어 갱신 보정 매일 0시
 * 10분(D-108), 한국 시간.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RunningSchedulingConfig.Schedule.class)
public class RunningSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ServerZone.ID;

    private final DisposalJob disposalJob;
    private final TierReconcileJob tierReconcileJob;
    private final Schedule schedule;

    public RunningSchedulingConfig(
            DisposalJob disposalJob, TierReconcileJob tierReconcileJob, Schedule schedule) {
        this.disposalJob = disposalJob;
        this.tierReconcileJob = tierReconcileJob;
        this.schedule = schedule;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(disposalJob::run, new CronTrigger(schedule.disposalCron(), SEOUL)));
        registrar.addCronTask(
                new CronTask(
                        tierReconcileJob::run,
                        new CronTrigger(schedule.tierReconcileCron(), SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param disposalCron runkk.batch.disposal-cron
     * @param tierReconcileCron runkk.batch.tier-reconcile-cron(기본 0시 10분, D-108)
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String disposalCron, String tierReconcileCron) {}
}
