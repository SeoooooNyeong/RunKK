package com.a207.runkk.batch.audit;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** batch.audit 작업의 실행 시각(설계 6.1.13 …SchedulingConfig, D-39). 기본 매일 3시(한국 시간). */
@Configuration
@EnableScheduling
public class AuditSchedulingConfig implements SchedulingConfigurer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AuditPurgeJob auditPurgeJob;
    private final String cron;

    public AuditSchedulingConfig(
            AuditPurgeJob auditPurgeJob, @Value("${runkk.batch.audit-purge-cron}") String cron) {
        this.auditPurgeJob = auditPurgeJob;
        this.cron = cron;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(auditPurgeJob::run, new CronTrigger(cron, SEOUL)));
    }
}
