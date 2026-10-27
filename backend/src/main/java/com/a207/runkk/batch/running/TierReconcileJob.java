package com.a207.runkk.batch.running;

import com.a207.runkk.running.TierReconcileService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시 10분에 티어 갱신 보정을 부른다(설계 5.5, D-108, runkk.batch.tier-reconcile-cron).
 * TierReconcileService.reconcile 하나만 부르고(D-39) 실패는 운영 로그로 남긴다.
 */
@Component
public class TierReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(TierReconcileJob.class);

    private final TierReconcileService tierReconcileService;
    private final Clock clock;

    public TierReconcileJob(TierReconcileService tierReconcileService, Clock clock) {
        this.tierReconcileService = tierReconcileService;
        this.clock = clock;
    }

    public void run() {
        try {
            tierReconcileService.reconcile(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("tier_reconcile_job_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
