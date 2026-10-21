package com.a207.runkk.batch.crew;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * batch.crew 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39): 1초 크루런 시계, 0시 주간 거리 재합산, 3시 가입 신청 만료(한국
 * 시간). 시험 프로필은 runkk.batch.crew-tick-enabled=false로 1초 시계를 끄고 연산을 직접 부른다.
 */
@Configuration
@EnableScheduling
public class CrewSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CrewRunTickJob crewRunTickJob;
    private final WeeklyRaceReconcileJob weeklyRaceReconcileJob;
    private final JoinRequestExpiryJob joinRequestExpiryJob;
    private final boolean tickEnabled;
    private final Duration tickInterval;
    private final String reconcileCron;
    private final String expiryCron;

    public CrewSchedulingConfig(
            CrewRunTickJob crewRunTickJob,
            WeeklyRaceReconcileJob weeklyRaceReconcileJob,
            JoinRequestExpiryJob joinRequestExpiryJob,
            @Value("${runkk.batch.crew-tick-enabled:true}") boolean tickEnabled,
            @Value("${runkk.batch.crew-tick-interval:1s}") Duration tickInterval,
            @Value("${runkk.batch.weekly-race-reconcile-cron}") String reconcileCron,
            @Value("${runkk.batch.join-request-expiry-cron}") String expiryCron) {
        this.crewRunTickJob = crewRunTickJob;
        this.weeklyRaceReconcileJob = weeklyRaceReconcileJob;
        this.joinRequestExpiryJob = joinRequestExpiryJob;
        this.tickEnabled = tickEnabled;
        this.tickInterval = tickInterval;
        this.reconcileCron = reconcileCron;
        this.expiryCron = expiryCron;
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
    }
}
