package com.a207.runkk.realtime;

import com.a207.runkk.battle.LiveBattleService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 진행값을 채널별 서비스로 넘긴다(설계 6.1.13 LiveSocketHandler.handleTextMessage, LivePollingController).
 * WebSocket과 폴링이 같은 경로를 쓴다. BATTLE이면 LiveBattleService.onProgress(회원, 채널 ID, 진행값, now). CREW_RUN은
 * crew 단계에서 CrewRunLiveService로 잇는다.
 */
@Component
class ProgressDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProgressDispatcher.class);

    private final LiveBattleService liveBattleService;
    private final Clock clock;

    ProgressDispatcher(LiveBattleService liveBattleService, Clock clock) {
        this.liveBattleService = liveBattleService;
        this.clock = clock;
    }

    void dispatch(long memberId, InboundProgress inbound) {
        LocalDateTime now = LocalDateTime.now(clock);
        switch (inbound.channel()) {
            case BATTLE ->
                    liveBattleService.onProgress(
                            memberId, inbound.channelId(), inbound.report(), now);
            case CREW_RUN -> log.debug("crew_run_progress_ignored reason=not_implemented");
        }
    }
}
