package com.a207.runkk.batch.member;

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
 * batch.member 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 탈퇴 자료 정리·만료 폐기 목록 정리 모두 기본 매일 3시(한국
 * 시간).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MemberSchedulingConfig.Schedule.class)
public class MemberSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WithdrawalPurgeJob withdrawalPurgeJob;
    private final RevocationPurgeJob revocationPurgeJob;
    private final String cron;
    private final String revocationCron;

    public MemberSchedulingConfig(
            WithdrawalPurgeJob withdrawalPurgeJob,
            RevocationPurgeJob revocationPurgeJob,
            Schedule schedule) {
        this.withdrawalPurgeJob = withdrawalPurgeJob;
        this.revocationPurgeJob = revocationPurgeJob;
        this.cron = schedule.withdrawalPurgeCron();
        this.revocationCron = schedule.revocationPurgeCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(withdrawalPurgeJob::run, new CronTrigger(cron, SEOUL)));
        registrar.addCronTask(
                new CronTask(revocationPurgeJob::run, new CronTrigger(revocationCron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param withdrawalPurgeCron runkk.batch.withdrawal-purge-cron
     * @param revocationPurgeCron runkk.batch.revocation-purge-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String withdrawalPurgeCron, String revocationPurgeCron) {}
}
