package com.a207.runkk.batch.running;

import com.a207.runkk.running.DisposalService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 원자료 파기 집행을 부른다(설계 6.1.13 batch.running, FR-026, FR-029). DisposalService.executeDue 하나만
 * 부른다(D-39).
 */
@Component
public class DisposalJob {

    private static final Logger log = LoggerFactory.getLogger(DisposalJob.class);

    private final DisposalService disposalService;
    private final Clock clock;

    public DisposalJob(DisposalService disposalService, Clock clock) {
        this.disposalService = disposalService;
        this.clock = clock;
    }

    public void run() {
        try {
            disposalService.executeDue(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("disposal_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
