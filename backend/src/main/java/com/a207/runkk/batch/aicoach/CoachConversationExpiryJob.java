package com.a207.runkk.batch.aicoach;

import com.a207.runkk.aicoach.AiCoachService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CoachConversationExpiryJob {
    private final AiCoachService coach;

    public CoachConversationExpiryJob(AiCoachService coach) {
        this.coach = coach;
    }

    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    public void purgeExpired() {
        coach.purgeExpired();
    }
}
