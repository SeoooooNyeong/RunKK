package com.a207.runkk.batch.crew;

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

/**
 * 크루런 매주 반복 회차 작업의 실행 시각(W6, D-성-26): 기본 매주 일요일 0시 5분, Asia/Seoul. CrewSchedulingConfig와 따로 둬 다른
 * crew 작업 설정을 건드리지 않는다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CrewRunRepeatSchedulingConfig.Schedule.class)
public class CrewRunRepeatSchedulingConfig implements SchedulingConfigurer {

    private final CrewRunRepeatJob crewRunRepeatJob;
    private final String cron;

    public CrewRunRepeatSchedulingConfig(CrewRunRepeatJob crewRunRepeatJob, Schedule schedule) {
        this.crewRunRepeatJob = crewRunRepeatJob;
        this.cron = schedule.crewRunRepeatCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(
                new CronTask(crewRunRepeatJob::run, new CronTrigger(cron, ServerZone.ID)));
    }

    /**
     * 실행 시각(runkk.batch.*).
     *
     * @param crewRunRepeatCron runkk.batch.crew-run-repeat-cron(기본 "0 5 0 * * SUN")
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(@DefaultValue("0 5 0 * * SUN") String crewRunRepeatCron) {}
}
