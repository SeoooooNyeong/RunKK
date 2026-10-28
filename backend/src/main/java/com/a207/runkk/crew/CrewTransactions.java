package com.a207.runkk.crew;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 크루 한 건 단위 트랜잭션(battle.BattleTransactions와 같은 방식). 크루런 모집글은 낙관적 잠금이라(5.3) 시계·러닝 연결·그만두기가 같은 글을 동시에
 * 바꾸면 늦은 쪽이 다시 읽어 재시도한다(최대 3회). 1초 시계와 0시 재합산은 글·크루마다 따로 감싸 한 건의 실패가 다른 건을 막지 않게 한다.
 */
@Component
class CrewTransactions {

    /** 낙관적 잠금 충돌 재시도 횟수(5.3). */
    static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_PAUSE_MILLIS = 20;

    private static final Logger log = LoggerFactory.getLogger(CrewTransactions.class);

    private final TransactionTemplate template;
    private final TransactionTemplate readOnly;

    CrewTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** 새 트랜잭션에서 실행하고 충돌하면 다시 읽어 최대 3회까지 재시도한다. */
    <T> T retrying(Supplier<T> work) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (OptimisticLockingFailureException e) {
                last = e;
                log.info("crew_conflict attempt={}", attempt);
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

    /**
     * 커밋 뒤 수신자 한 건(코드 리뷰 2차 M4). 새 트랜잭션에서 하고 일시 오류(낙관적 잠금 충돌·잠금 대기·연결 일시 장애)면 최대 3회 다시 한다. 그래도 실패하면
     * job과 key를 담은 실패 로그만 남기고 삼켜 업로드 응답과 뒤 수신자를 막지 않는다. 유실분은 각 수신자의 복구 경로가 맞춘다.
     */
    void afterCommit(String job, Object key, Runnable work) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                template.executeWithoutResult(status -> work.run());
                return;
            } catch (TransientDataAccessException e) {
                log.info("crew_receiver_retry job={} key={} attempt={}", job, key, attempt);
                if (attempt == MAX_ATTEMPTS) {
                    log.error(
                            "crew_receiver_failed job={} key={} error={}",
                            job,
                            key,
                            e.getClass().getSimpleName(),
                            e);
                } else {
                    pause(attempt);
                }
            } catch (RuntimeException e) {
                log.error(
                        "crew_receiver_failed job={} key={} error={}",
                        job,
                        key,
                        e.getClass().getSimpleName(),
                        e);
                return;
            }
        }
    }

    /** 일시 오류 재시도 사이에 잠깐 쉰다(20·40ms, 잠금을 쥔 쪽이 끝낼 틈). */
    private static void pause(int attempt) {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    <T> T read(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }

    /** 새 트랜잭션에서 한 번 실행한다. 실패하면 로그만 남기고 기본값(시계·재합산의 한 건, 다음 차례에 다시). */
    <T> T once(String job, Supplier<T> work, T fallback) {
        try {
            return template.execute(status -> work.get());
        } catch (RuntimeException e) {
            log.warn("crew_job_failed job={} error={}", job, e.getClass().getSimpleName(), e);
            return fallback;
        }
    }
}
