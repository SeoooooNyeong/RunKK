package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.WeeklyRaceService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시에 크루 주간 거리 재합산을 부른다(설계 6.1.13 batch.crew, D-38, D-47). now만 넘기고 대상 크루·주는 WeeklyRaceService가
 * 고른다. 실패는 운영 로그로 남긴다.
 */
@Component
public class WeeklyRaceReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklyRaceReconcileJob.class);

    private final WeeklyRaceService weeklyRaceService;
    private final Clock clock;

    public WeeklyRaceReconcileJob(WeeklyRaceService weeklyRaceService, Clock clock) {
        this.weeklyRaceService = weeklyRaceService;
        this.clock = clock;
    }

    public void run() {
        try {
            weeklyRaceService.reconcileAll(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("weekly_race_reconcile_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
