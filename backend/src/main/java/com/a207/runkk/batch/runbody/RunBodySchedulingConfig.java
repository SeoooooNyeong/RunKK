package com.a207.runkk.batch.runbody;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * batch.runbody 작업의 실행 간격(설계 05-19 D-443, 6.1.13 …SchedulingConfig). 런바디 작업 진행 기본 5초. 운영 기준값
 * RUNBODY_ENABLED가 꺼져 있으면 pollDue가 바로 돌아온다. 시험 프로필은 runkk.batch.runbody-poll-enabled=false로 끄고 연산을
 * 직접 부른다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RunBodySchedulingConfig.Schedule.class)
@ConditionalOnProperty(
        name = "runkk.batch.runbody-poll-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RunBodySchedulingConfig implements SchedulingConfigurer {

    private final RunBodyPollJob pollJob;
    private final Duration interval;

    public RunBodySchedulingConfig(RunBodyPollJob pollJob, Schedule schedule) {
        this.pollJob = pollJob;
        this.interval = schedule.runbodyPollInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(pollJob::run, interval);
    }

    /**
     * 이 패키지 작업의 실행 간격(runkk.batch.*).
     *
     * @param runbodyPollInterval runkk.batch.runbody-poll-interval(기본 5초, D-443)
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(@DefaultValue("5s") Duration runbodyPollInterval) {}
}
