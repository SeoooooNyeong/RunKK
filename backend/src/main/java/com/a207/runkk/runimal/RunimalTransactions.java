package com.a207.runkk.runimal;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runimal 판정 한 건 단위 트랜잭션(battle.BattleTransactions와 같은 방식). 같은 회원의 기록 두 건이 거의 동시에 저장되면 비동기 수신자 두 개가
 * 같은 판정 상태 행을 함께 만들거나(기본 키 충돌) 고쳐(낙관적 잠금 충돌) 늦은 쪽이 실패한다. 다시 읽어 최대 3회까지 재시도한다(B3 리뷰).
 */
@Component
class RunimalTransactions {

    /** 충돌 재시도 횟수. */
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(RunimalTransactions.class);

    private final TransactionTemplate template;

    RunimalTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 새 트랜잭션에서 실행하고 동시 변경 충돌이면 최대 3회까지 다시 한다. 세 번 모두 충돌하면 그 예외를 던진다. */
    <T> T retrying(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                last = e;
                log.info("runimal_conflict attempt={}", attempt);
            }
        }
        throw last;
    }
}
