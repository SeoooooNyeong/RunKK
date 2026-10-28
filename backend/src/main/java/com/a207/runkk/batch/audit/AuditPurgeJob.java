package com.a207.runkk.batch.audit;

import com.a207.runkk.audit.AuditService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 3시에 보관 기한(6개월) 지난 감사 기록 삭제를 부른다(설계 6.1.13 batch.audit, FR-176, NFR-035, D-39). */
@Component
public class AuditPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AuditPurgeJob.class);

    private final AuditService auditService;
    private final Clock clock;

    public AuditPurgeJob(AuditService auditService, Clock clock) {
        this.auditService = auditService;
        this.clock = clock;
    }

    public void run() {
        try {
            auditService.purgeExpired(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("audit_purge_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
