package com.a207.runkk.realtime;

import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.crew.CrewRunLiveService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 진행값을 채널별 서비스로 넘긴다(설계 6.1.13 LiveSocketHandler.handleTextMessage, LivePollingController).
 * WebSocket과 폴링이 같은 경로를 쓴다. BATTLE이면 LiveBattleService.onProgress, CREW_RUN이면
 * CrewRunLiveService.onProgress(회원, 채널 ID, 진행값, now). 채널 ID는 대결 ID 또는 크루런 게시글 ID다(D-48).
 */
@Component
class ProgressDispatcher {

    private final LiveBattleService liveBattleService;
    private final CrewRunLiveService crewRunLiveService;
    private final Clock clock;

    ProgressDispatcher(
            LiveBattleService liveBattleService,
            CrewRunLiveService crewRunLiveService,
            Clock clock) {
        this.liveBattleService = liveBattleService;
        this.crewRunLiveService = crewRunLiveService;
        this.clock = clock;
    }

    void dispatch(long memberId, InboundProgress inbound) {
        LocalDateTime now = LocalDateTime.now(clock);
        switch (inbound.channel()) {
            case BATTLE ->
                    liveBattleService.onProgress(
                            memberId, inbound.channelId(), inbound.report(), now);
            case CREW_RUN ->
                    crewRunLiveService.onProgress(
                            memberId, inbound.channelId(), inbound.report(), now);
        }
    }
}
