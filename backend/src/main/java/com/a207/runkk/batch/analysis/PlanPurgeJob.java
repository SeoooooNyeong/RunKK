package com.a207.runkk.batch.analysis;

import com.a207.runkk.analysis.CoachService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 3시에 보존 기간이 지난 계획·주간 목표 행을 지운다(B5 리뷰). CoachService.purgePastPlans 하나만 부른다(D-39). */
@Component
public class PlanPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(PlanPurgeJob.class);

    private final CoachService coachService;
    private final Clock clock;

    public PlanPurgeJob(CoachService coachService, Clock clock) {
        this.coachService = coachService;
        this.clock = clock;
    }

    public void run() {
        try {
            int removed = coachService.purgePastPlans(LocalDate.now(clock));
            log.info("plan_purge removed={}", removed);
        } catch (RuntimeException e) {
            log.error("plan_purge_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
