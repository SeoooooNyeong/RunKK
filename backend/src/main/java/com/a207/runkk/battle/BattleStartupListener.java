package com.a207.runkk.battle;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 작업(설계 6.1.6, 5.5 시작 (2)·(3), D-55). 시작 작업을 가진 패키지가 시작 사건을 직접 받아 bootstrap → battle 화살표를 만들지
 * 않는다(design-review.md E12). 한쪽이 실패해도 다른 쪽과 서버 기동은 이어 간다.
 */
@Component
public class BattleStartupListener {

    private static final Logger log = LoggerFactory.getLogger(BattleStartupListener.class);

    private final VirtualRecordService virtualRecordService;
    private final BattleClockService battleClockService;
    private final Clock clock;

    public BattleStartupListener(
            VirtualRecordService virtualRecordService,
            BattleClockService battleClockService,
            Clock clock) {
        this.virtualRecordService = virtualRecordService;
        this.battleClockService = battleClockService;
        this.clock = clock;
    }

    /** VirtualRecordService.ensureGenerated() → BattleClockService.resumeLiveSessions(now). */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent e) {
        try {
            virtualRecordService.ensureGenerated();
        } catch (RuntimeException failure) {
            log.error(
                    "virtual_records_startup_failed error={}",
                    failure.getClass().getSimpleName(),
                    failure);
        }
        try {
            battleClockService.resumeLiveSessions(LocalDateTime.now(clock));
        } catch (RuntimeException failure) {
            log.error(
                    "live_sessions_resume_failed error={}",
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }
}
