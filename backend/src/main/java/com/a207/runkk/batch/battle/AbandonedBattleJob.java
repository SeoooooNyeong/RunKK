package com.a207.runkk.batch.battle;

import com.a207.runkk.battle.BattleStartService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 1분마다 출발하지 못한 대결 정리를 부른다(활성 대결 1개 불변식 결정, D-39): BattleStartService.cancelAbandoned(now). 업무 규칙이
 * 없고 실패는 운영 로그로 남긴다.
 */
@Component
public class AbandonedBattleJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonedBattleJob.class);

    private final BattleStartService battleStartService;
    private final Clock clock;

    public AbandonedBattleJob(BattleStartService battleStartService, Clock clock) {
        this.battleStartService = battleStartService;
        this.clock = clock;
    }

    public void run() {
        try {
            battleStartService.cancelAbandoned(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("battle_abandon_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
