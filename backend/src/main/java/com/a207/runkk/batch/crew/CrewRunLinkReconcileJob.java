package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.CrewRunLinkReconcileService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시 10분에 크루런 기록 연결 일 단위 보정을 부른다(코드 리뷰 2차 M4 결정, D-38·D-39와 같은 방식). now만 넘기고 대상 기록은
 * CrewRunLinkReconcileService가 고른다. 실패는 운영 로그로 남긴다.
 */
@Component
public class CrewRunLinkReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(CrewRunLinkReconcileJob.class);

    private final CrewRunLinkReconcileService crewRunLinkReconcileService;
    private final Clock clock;

    public CrewRunLinkReconcileJob(
            CrewRunLinkReconcileService crewRunLinkReconcileService, Clock clock) {
        this.crewRunLinkReconcileService = crewRunLinkReconcileService;
        this.clock = clock;
    }

    public void run() {
        try {
            crewRunLinkReconcileService.reconcileLinks(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("crew_run_link_reconcile_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
