package com.a207.runkk.batch.avatar;

import com.a207.runkk.avatar.BackgroundService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시 10분에 랜드마크 배경 해금 보정을 부른다(설계 5.5, D-107 (3), D-39). now만 넘기고 대상 회원은 BackgroundService가 고른다(최근 2일
 * 저장 기록 회원). 실패는 운영 로그로 남긴다.
 */
@Component
public class BackgroundReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(BackgroundReconcileJob.class);

    private final BackgroundService backgroundService;
    private final Clock clock;

    public BackgroundReconcileJob(BackgroundService backgroundService, Clock clock) {
        this.backgroundService = backgroundService;
        this.clock = clock;
    }

    public void run() {
        try {
            backgroundService.reconcile(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("background_reconcile_job_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
