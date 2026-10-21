package com.a207.runkk.batch.crew;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * batch.crew 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39): 1초 크루런 시계, 0시 주간 거리 재합산, 0시 10분 크루런 기록
 * 연결 보정(M4 결정), 3시 가입 신청 만료(한국 시간). 시험 프로필은 runkk.batch.crew-tick-enabled=false로 1초 시계를 끄고 연산을 직접
 * 부른다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CrewSchedulingConfig.Schedule.class)
public class CrewSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CrewRunTickJob crewRunTickJob;
    private final WeeklyRaceReconcileJob weeklyRaceReconcileJob;
    private final JoinRequestExpiryJob joinRequestExpiryJob;
    private final CrewRunLinkReconcileJob linkReconcileJob;
    private final boolean tickEnabled;
    private final Duration tickInterval;
    private final String reconcileCron;
    private final String expiryCron;
    private final String linkReconcileCron;

    public CrewSchedulingConfig(
            CrewRunTickJob crewRunTickJob,
            WeeklyRaceReconcileJob weeklyRaceReconcileJob,
            JoinRequestExpiryJob joinRequestExpiryJob,
            CrewRunLinkReconcileJob linkReconcileJob,
            Schedule schedule) {
        this.crewRunTickJob = crewRunTickJob;
        this.weeklyRaceReconcileJob = weeklyRaceReconcileJob;
        this.joinRequestExpiryJob = joinRequestExpiryJob;
        this.linkReconcileJob = linkReconcileJob;
        this.tickEnabled = schedule.crewTickEnabled();
        this.tickInterval = schedule.crewTickInterval();
        this.reconcileCron = schedule.weeklyRaceReconcileCron();
        this.expiryCron = schedule.joinRequestExpiryCron();
        this.linkReconcileCron = schedule.crewRunLinkReconcileCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (tickEnabled) {
            registrar.addFixedDelayTask(crewRunTickJob::run, tickInterval);
        }
        registrar.addCronTask(
                new CronTask(weeklyRaceReconcileJob::run, new CronTrigger(reconcileCron, SEOUL)));
        registrar.addCronTask(
                new CronTask(joinRequestExpiryJob::run, new CronTrigger(expiryCron, SEOUL)));
        registrar.addCronTask(
                new CronTask(linkReconcileJob::run, new CronTrigger(linkReconcileCron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param crewTickEnabled runkk.batch.crew-tick-enabled
     * @param crewTickInterval runkk.batch.crew-tick-interval
     * @param weeklyRaceReconcileCron runkk.batch.weekly-race-reconcile-cron
     * @param joinRequestExpiryCron runkk.batch.join-request-expiry-cron
     * @param crewRunLinkReconcileCron runkk.batch.crew-run-link-reconcile-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(
            @DefaultValue("true") boolean crewTickEnabled,
            @DefaultValue("1s") Duration crewTickInterval,
            String weeklyRaceReconcileCron,
            String joinRequestExpiryCron,
            String crewRunLinkReconcileCron) {}
}
