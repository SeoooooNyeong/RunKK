package com.a207.runkk.analysis;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * analysis의 짧은 트랜잭션(avatar.AvatarTransactions와 같은 방식). 코멘트 생성(ai 호출 최대 3초) 동안 리포트 행을 잡지 않도록 읽기 → 생성
 * → 다시 읽고 붙이기를 나누고, 0시·20시 작업은 대상마다 따로 감싸 한 건의 실패가 다른 건을 막지 않게 한다.
 */
@Component
class AnalysisTransactions {

    private final TransactionTemplate template;
    private final TransactionTemplate readOnly;

    AnalysisTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    <T> T write(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    <T> T read(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }
}
