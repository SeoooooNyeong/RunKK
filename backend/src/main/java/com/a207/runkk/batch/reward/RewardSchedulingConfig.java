package com.a207.runkk.batch.reward;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.reward 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 0시 10분(한국 시간, 전날이 끝난 뒤). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RewardSchedulingConfig.Schedule.class)
public class RewardSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RewardReconcileJob rewardReconcileJob;
    private final String cron;

    public RewardSchedulingConfig(RewardReconcileJob rewardReconcileJob, Schedule schedule) {
        this.rewardReconcileJob = rewardReconcileJob;
        this.cron = schedule.rewardReconcileCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(rewardReconcileJob::run, new CronTrigger(cron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param rewardReconcileCron runkk.batch.reward-reconcile-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String rewardReconcileCron) {}
}
