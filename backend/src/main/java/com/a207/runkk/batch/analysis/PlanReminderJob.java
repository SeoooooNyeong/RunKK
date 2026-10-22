package com.a207.runkk.batch.analysis;

import com.a207.runkk.analysis.CoachService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 20시에 계획 미이행 알림을 부른다(설계 6.1.13 batch.analysis, FR-075, D-39). CoachService.remindMissedPlans 하나만
 * 부른다.
 */
@Component
public class PlanReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PlanReminderJob.class);

    private final CoachService coachService;
    private final Clock clock;

    public PlanReminderJob(CoachService coachService, Clock clock) {
        this.coachService = coachService;
        this.clock = clock;
    }

    public void run() {
        try {
            coachService.remindMissedPlans(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("plan_reminder_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
