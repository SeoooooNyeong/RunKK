package com.a207.runkk.crew;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 실시간 메시지·세션 생성처럼 되돌릴 수 없는 부수 효과를 커밋 뒤로 미룬다(battle.AfterCommit과 같은 방식). 낙관적 잠금 충돌로 재시도하거나 롤백되면 메시지가
 * 두 번 나가거나 없는 크루런의 메시지가 남는 일을 막는다. 트랜잭션 밖이면 바로 실행한다. 한 효과의 실패(Redis·소켓)는 로그만 남겨 뒤따르는 효과와 호출한 쪽을 막지
 * 않는다. 세션이 빠지면 1초 시계가 다시 연다(CrewRunAnnouncer.ensureSession).
 */
final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {}

    static void run(Runnable effect) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded(effect);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        guarded(effect);
                    }
                });
    }

    private static void guarded(Runnable effect) {
        try {
            effect.run();
        } catch (RuntimeException e) {
            log.warn("crew_after_commit_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
