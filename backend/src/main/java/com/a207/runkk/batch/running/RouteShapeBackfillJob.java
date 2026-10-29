package com.a207.runkk.batch.running;

import com.a207.runkk.running.RouteShapeBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동이 끝나면 경로 모양이 없는 기존 기록에 목록 썸네일 모양을 채운다(설계 5.6.2, D-234). RunRejudgeJob과 같은 방식으로 공용 비동기 실행기에서 남은
 * 것이 없을 때까지 BATCH개씩 부른다. 실패는 운영 로그로 남기고 기동은 막지 않는다(남은 기록은 다음 기동이 잇는다).
 * runkk.batch.route-shape-backfill.enabled=false로 끈다.
 */
@Component
@ConditionalOnProperty(
        name = "runkk.batch.route-shape-backfill.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RouteShapeBackfillJob {

    /** 한 번에 채울 기록 수(설계 fillMissing(200)). */
    static final int BATCH = 200;

    private static final Logger log = LoggerFactory.getLogger(RouteShapeBackfillJob.class);

    private final RouteShapeBackfillService routeShapeBackfillService;

    public RouteShapeBackfillJob(RouteShapeBackfillService routeShapeBackfillService) {
        this.routeShapeBackfillService = routeShapeBackfillService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int total = 0;
            int filled;
            do {
                filled = routeShapeBackfillService.fillMissing(BATCH);
                total += filled;
            } while (filled > 0);
            log.info("route_shape_backfill_done filled={}", total);
        } catch (RuntimeException e) {
            log.error("route_shape_backfill_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
