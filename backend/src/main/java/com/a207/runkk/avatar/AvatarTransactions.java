package com.a207.runkk.avatar;

import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아바타 한 건 단위 트랜잭션(battle.BattleTransactions와 같은 방식). 셀카 변환(최대 수 초) 동안 행을 잠그지 않도록 검사 → 변환 → 다시 잠그고
 * 재검사 → 저장을 짧은 트랜잭션으로 나누고(5.3), 0시 외형 재계산은 회원마다 따로 감싸 한 회원의 실패가 다른 회원을 막지 않게 한다.
 */
@Component
class AvatarTransactions {

    private static final Logger log = LoggerFactory.getLogger(AvatarTransactions.class);

    private final TransactionTemplate template;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate readApart;

    AvatarTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.readApart = new TransactionTemplate(transactionManager);
        this.readApart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readApart.setReadOnly(true);
    }

    <T> T write(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    <T> T read(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }

    /**
     * 부르는 쪽 트랜잭션과 떨어진 새 읽기 트랜잭션에서 읽는다. 실패하면 경고 로그만 남기고 비어 있다. 부르는 쪽 트랜잭션에 참여하면 예외를 잡아도 그 트랜잭션이
     * rollback-only가 되고(PostgreSQL은 한 문장이 실패하면 트랜잭션 전체를 못 쓴다) 업무가 함께 실패하므로 떼어 놓는다. 부르는 쪽이 트랜잭션 안이면
     * 읽는 동안 DB 연결을 하나 더 쓴다.
     */
    <T> Optional<T> readApart(String job, long memberId, Supplier<T> work) {
        try {
            return Optional.ofNullable(readApart.execute(status -> work.get()));
        } catch (RuntimeException e) {
            log.warn(
                    "avatar_read_failed job={} memberId={} error={}",
                    job,
                    memberId,
                    e.getClass().getSimpleName(),
                    e);
            return Optional.empty();
        }
    }

    /** 새 트랜잭션에서 한 번 실행한다. 실패하면 로그만 남기고 비어 있다. */
    <T> Optional<T> once(String job, Supplier<T> work) {
        try {
            return Optional.ofNullable(template.execute(status -> work.get()));
        } catch (RuntimeException e) {
            log.warn("avatar_job_failed job={} error={}", job, e.getClass().getSimpleName(), e);
            return Optional.empty();
        }
    }
}
