package com.a207.runkk.batch.avatar;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.avatar 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 0시(한국 시간, N-18). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AvatarSchedulingConfig.Schedule.class)
public class AvatarSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AppearanceRefreshJob appearanceRefreshJob;
    private final String cron;

    public AvatarSchedulingConfig(AppearanceRefreshJob appearanceRefreshJob, Schedule schedule) {
        this.appearanceRefreshJob = appearanceRefreshJob;
        this.cron = schedule.appearanceRefreshCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(appearanceRefreshJob::run, new CronTrigger(cron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param appearanceRefreshCron runkk.batch.appearance-refresh-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String appearanceRefreshCron) {}
}
