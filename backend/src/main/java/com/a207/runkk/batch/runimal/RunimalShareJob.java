package com.a207.runkk.batch.runimal;

import com.a207.runkk.runimal.RunimalTypeService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 매일 0시 Runimal 유형 비율을 다시 계산한다(설계 5.6.2, SQ-34, FR-226, D-246):
 * RunimalTypeService.recomputeShares(now). 업무 규칙이 없고 실패는 운영 로그 runimal_share_failed로 남기며 전날 값이 남는다.
 */
@Component
public class RunimalShareJob {

    private static final Logger log = LoggerFactory.getLogger(RunimalShareJob.class);

    private final RunimalTypeService runimalTypeService;
    private final Clock clock;

    public RunimalShareJob(RunimalTypeService runimalTypeService, Clock clock) {
        this.runimalTypeService = runimalTypeService;
        this.clock = clock;
    }

    public void run() {
        try {
            runimalTypeService.recomputeShares(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("runimal_share_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
