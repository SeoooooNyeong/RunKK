package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.CrewPostService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 1분마다 투표 마감 1시간 전 알림을 부른다(설계 6.1.13 batch.crew, 6.2 A-28, FR-218, D-106). 1초 크루런 시계에 얹지 않는다. 실패는
 * 운영 로그로 남기고 다음 분에 다시 본다(reminded가 거짓인 투표만 고르므로 두 번 보내지 않는다).
 */
@Component
public class PollReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PollReminderJob.class);

    private final CrewPostService crewPostService;
    private final Clock clock;

    public PollReminderJob(CrewPostService crewPostService, Clock clock) {
        this.crewPostService = crewPostService;
        this.clock = clock;
    }

    public void run() {
        try {
            crewPostService.remindClosingPolls(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("poll_reminder_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
