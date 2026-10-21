package com.a207.runkk.batch.reward;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.reward 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 0시 10분(한국 시간, 전날이 끝난 뒤). */
@Configuration
@EnableScheduling
public class RewardSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RewardReconcileJob rewardReconcileJob;
    private final String cron;

    public RewardSchedulingConfig(
            RewardReconcileJob rewardReconcileJob,
            @Value("${runkk.batch.reward-reconcile-cron}") String cron) {
        this.rewardReconcileJob = rewardReconcileJob;
        this.cron = cron;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(rewardReconcileJob::run, new CronTrigger(cron, SEOUL)));
    }
}
