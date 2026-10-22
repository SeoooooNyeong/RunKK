package com.a207.runkk.battle;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대결 한 건 단위 트랜잭션(설계 5.3 "대결 도착·판정"). LiveBattleService와 BattleResultService가 같은 대결을 동시에 바꾸면 늦은 쪽이
 * 낙관적 잠금 충돌을 받고 다시 읽어 재시도한다(최대 3회). 1초 시계는 대결마다 따로 감싸 한 대결의 실패가 다른 대결을 막지 않게 하고, 실패한 대결은 다음 tick에
 * 다시 본다.
 */
@Component
class BattleTransactions {

    /** 낙관적 잠금 충돌 재시도 횟수(5.3). */
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(BattleTransactions.class);

    private final TransactionTemplate template;

    BattleTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 새 트랜잭션에서 실행하고 충돌하면 다시 읽어 최대 3회까지 재시도한다. 세 번 모두 충돌하면 그 예외를 던진다. */
    <T> T retrying(Supplier<T> work) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (OptimisticLockingFailureException e) {
                last = e;
                log.info("battle_conflict attempt={}", attempt);
            }
        }
        throw last;
    }

    void retryingRun(Runnable work) {
        retrying(
                () -> {
                    work.run();
                    return Boolean.TRUE;
                });
    }

    /** 새 트랜잭션에서 한 번 실행한다. 실패하면 로그만 남기고 false(1초 시계의 대결 한 건, 다음 tick에 다시). */
    boolean once(String job, Runnable work) {
        try {
            template.executeWithoutResult(status -> work.run());
            return true;
        } catch (RuntimeException e) {
            log.warn("battle_job_failed job={} error={}", job, e.getClass().getSimpleName(), e);
            return false;
        }
    }
}
