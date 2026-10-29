package com.a207.runkk.batch.runimal;

import com.a207.runkk.common.ServerZone;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.runimal 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, v0.14 D-246): 0시 유형 비율 재계산(한국 시간). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RunimalSchedulingConfig.Schedule.class)
public class RunimalSchedulingConfig implements SchedulingConfigurer {

    private final RunimalShareJob runimalShareJob;
    private final String shareCron;

    public RunimalSchedulingConfig(RunimalShareJob runimalShareJob, Schedule schedule) {
        this.runimalShareJob = runimalShareJob;
        this.shareCron = schedule.runimalShareCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(runimalShareJob::run, new CronTrigger(shareCron, ServerZone.ID)));
    }

    /**
     * 이 패키지 작업의 실행 시각(runkk.batch.*).
     *
     * @param runimalShareCron runkk.batch.runimal-share-cron(기본 매일 0시)
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(@DefaultValue("0 0 0 * * *") String runimalShareCron) {}
}
