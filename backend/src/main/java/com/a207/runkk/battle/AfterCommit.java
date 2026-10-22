package com.a207.runkk.battle;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 실시간 메시지·세션 정리처럼 되돌릴 수 없는 부수 효과를 커밋 뒤로 미룬다. 낙관적 잠금 충돌로 재시도하거나 롤백되면 메시지가 두 번 나가거나 취소되지 않은 메시지가 남는
 * 일을 막는다(5.3). 트랜잭션 밖이면 바로 실행한다.
 */
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable effect) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            effect.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        effect.run();
                    }
                });
    }
}
