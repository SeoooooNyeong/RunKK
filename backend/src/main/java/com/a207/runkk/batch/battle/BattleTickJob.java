package com.a207.runkk.batch.battle;

import com.a207.runkk.battle.BattleClockService;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.battle.Matchmaker;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 1초마다 대결 시계 연산을 부른다(설계 6.1.13 batch.battle, 5.5 시각 작업, D-39): Matchmaker.tick,
 * BattleClockService.tick, LiveBattleService.detectTimeouts. 같은 now를 넘기고, 한 호출의 실패가 다른 호출을 막지 않게
 * 호출마다 따로 감싼다. 업무 규칙이 없다.
 */
@Component
public class BattleTickJob {

    private static final Logger log = LoggerFactory.getLogger(BattleTickJob.class);

    private final Matchmaker matchmaker;
    private final BattleClockService battleClockService;
    private final LiveBattleService liveBattleService;
    private final Clock clock;

    public BattleTickJob(
            Matchmaker matchmaker,
            BattleClockService battleClockService,
            LiveBattleService liveBattleService,
            Clock clock) {
        this.matchmaker = matchmaker;
        this.battleClockService = battleClockService;
        this.liveBattleService = liveBattleService;
        this.clock = clock;
    }

    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        guarded("matchmaker_tick", matchmaker::tick, now);
        guarded("battle_clock_tick", battleClockService::tick, now);
        guarded("live_timeouts", liveBattleService::detectTimeouts, now);
    }

    private static void guarded(String job, Consumer<LocalDateTime> call, LocalDateTime now) {
        try {
            call.accept(now);
        } catch (RuntimeException e) {
            log.error("{}_failed error={}", job, e.getClass().getSimpleName(), e);
        }
    }
}
