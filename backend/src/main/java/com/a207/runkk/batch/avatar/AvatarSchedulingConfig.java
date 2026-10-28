package com.a207.runkk.batch.avatar;

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
 * batch.avatar 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 외형 재계산 기본 매일 0시(한국 시간, N-18), 배경 해금 보정
 * 0시 10분(D-107).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AvatarSchedulingConfig.Schedule.class)
public class AvatarSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ServerZone.ID;

    private final AppearanceRefreshJob appearanceRefreshJob;
    private final BackgroundReconcileJob backgroundReconcileJob;
    private final Schedule schedule;

    public AvatarSchedulingConfig(
            AppearanceRefreshJob appearanceRefreshJob,
            BackgroundReconcileJob backgroundReconcileJob,
            Schedule schedule) {
        this.appearanceRefreshJob = appearanceRefreshJob;
        this.backgroundReconcileJob = backgroundReconcileJob;
        this.schedule = schedule;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(
                        appearanceRefreshJob::run,
                        new CronTrigger(schedule.appearanceRefreshCron(), SEOUL)));
        registrar.addCronTask(
                new CronTask(
                        backgroundReconcileJob::run,
                        new CronTrigger(schedule.backgroundReconcileCron(), SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param appearanceRefreshCron runkk.batch.appearance-refresh-cron
     * @param backgroundReconcileCron runkk.batch.background-reconcile-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String appearanceRefreshCron, String backgroundReconcileCron) {}
}
