package com.a207.runkk.batch.aiprivacy;

import com.a207.runkk.aiprivacy.AiPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 3시 잔여 파기 배치 뒤 매일 3시 5분(한국 시간)에 실패분을 재시도한다. */
@Component
public class AiPurgeRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AiPurgeRetryJob.class);
    private final AiPurgeService service;

    public AiPurgeRetryJob(AiPurgeService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 5 3 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            service.retryDue();
        } catch (RuntimeException error) {
            log.error("ai_purge_batch_failed error={}", error.getClass().getSimpleName());
        }
    }
}
