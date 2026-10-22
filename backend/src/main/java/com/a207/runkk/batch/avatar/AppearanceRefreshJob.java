package com.a207.runkk.batch.avatar;

import com.a207.runkk.avatar.AppearanceService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 0시에 아바타 외형 재계산을 부른다(설계 6.1.13 batch.avatar, FR-163~FR-165, D-39). 실패는 운영 로그로 남긴다. */
@Component
public class AppearanceRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(AppearanceRefreshJob.class);

    private final AppearanceService appearanceService;
    private final Clock clock;

    public AppearanceRefreshJob(AppearanceService appearanceService, Clock clock) {
        this.appearanceService = appearanceService;
        this.clock = clock;
    }

    public void run() {
        try {
            appearanceService.refreshAll(LocalDate.now(clock));
        } catch (RuntimeException e) {
            log.error("appearance_refresh_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
