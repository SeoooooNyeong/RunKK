package com.a207.runkk.batch.avatar;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.avatar 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 0시(한국 시간, N-18). */
@Configuration
@EnableScheduling
public class AvatarSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AppearanceRefreshJob appearanceRefreshJob;
    private final String cron;

    public AvatarSchedulingConfig(
            AppearanceRefreshJob appearanceRefreshJob,
            @Value("${runkk.batch.appearance-refresh-cron}") String cron) {
        this.appearanceRefreshJob = appearanceRefreshJob;
        this.cron = cron;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(appearanceRefreshJob::run, new CronTrigger(cron, SEOUL)));
    }
}
