package com.a207.runkk.batch.audit;

import com.a207.runkk.common.ServerZone;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.audit 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 3시(한국 시간). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuditSchedulingConfig.Schedule.class)
public class AuditSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ServerZone.ID;

    private final AuditPurgeJob auditPurgeJob;
    private final String cron;

    public AuditSchedulingConfig(AuditPurgeJob auditPurgeJob, Schedule schedule) {
        this.auditPurgeJob = auditPurgeJob;
        this.cron = schedule.auditPurgeCron();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(auditPurgeJob::run, new CronTrigger(cron, SEOUL)));
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param auditPurgeCron runkk.batch.audit-purge-cron
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(String auditPurgeCron) {}
}
