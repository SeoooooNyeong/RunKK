package com.a207.runkk.batch.battle;

import com.a207.runkk.battle.PlacementReconcileService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시 20분에 배치고사 판 셈 누락 복구를 부른다(05-21 D-490, runkk.batch.placement-reconcile-cron, 티어 보정 0시 10분 다음).
 * PlacementReconcileService.reconcile 하나만 부르고 실패는 운영 로그로 남긴다.
 */
@Component
public class PlacementReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(PlacementReconcileJob.class);

    private final PlacementReconcileService placementReconcileService;
    private final Clock clock;

    public PlacementReconcileJob(PlacementReconcileService placementReconcileService, Clock clock) {
        this.placementReconcileService = placementReconcileService;
        this.clock = clock;
    }

    public void run() {
        try {
            placementReconcileService.reconcile(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("placement_reconcile_job_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
