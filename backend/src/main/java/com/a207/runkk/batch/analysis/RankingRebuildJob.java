package com.a207.runkk.batch.analysis;

import com.a207.runkk.analysis.RankingService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 0시에 랭킹 스냅숏 재집계를 부른다(설계 6.1.13 batch.analysis, D-25, D-39). RankingService.rebuild 하나만 부른다. */
@Component
public class RankingRebuildJob {

    private static final Logger log = LoggerFactory.getLogger(RankingRebuildJob.class);

    private final RankingService rankingService;
    private final Clock clock;

    public RankingRebuildJob(RankingService rankingService, Clock clock) {
        this.rankingService = rankingService;
        this.clock = clock;
    }

    public void run() {
        try {
            rankingService.rebuild(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("ranking_rebuild_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
