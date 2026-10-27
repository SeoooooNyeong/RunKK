package com.a207.runkk.avatar;

import com.a207.runkk.running.RunRecordRejudged;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 기록 저장 커밋 뒤 비동기로 랜드마크 배경을 해금한다(설계 6.1.8, FR-201, D-107, D-129). 저장 응답에 쓰이지 않으므로 리포트·Runimal과 같은
 * 실행기에서 돈다. 유효 기록만 본다. 실패는 운영 로그만 남기고 BackgroundService.reconcile(0시 10분)이 채운다.
 */
@Component
public class BackgroundUnlockListener {

    private static final Logger log = LoggerFactory.getLogger(BackgroundUnlockListener.class);

    private final BackgroundService backgroundService;
    private final Clock clock;

    BackgroundUnlockListener(BackgroundService backgroundService, Clock clock) {
        this.backgroundService = backgroundService;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        try {
            backgroundService.unlockReached(e.memberId(), LocalDateTime.now(clock));
        } catch (RuntimeException failure) {
            log.error(
                    "background_unlock_failed memberId={} recordId={} error={}",
                    e.memberId(),
                    e.recordId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    /**
     * v0.10 일회성 재판정으로 유효가 된 기록(RunRecordRejudged). 늘어난 누적 유효 거리로 넘은 랜드마크 배경을 알림 없이 해금한다(사용자 결정:
     * 재판정은 푸시를 보내지 않는다). 재판정 작업 안에서 동기로 돈다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunRejudged(RunRecordRejudged e) {
        try {
            backgroundService.unlockReachedQuietly(e.memberId(), LocalDateTime.now(clock));
        } catch (RuntimeException failure) {
            log.error(
                    "background_unlock_failed memberId={} recordId={} error={}",
                    e.memberId(),
                    e.recordId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }
}
