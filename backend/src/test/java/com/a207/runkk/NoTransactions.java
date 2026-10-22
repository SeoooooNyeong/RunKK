package com.a207.runkk;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 단위 시험용 트랜잭션 관리자: 트랜잭션 없이 바로 실행한다(인메모리 저장소와 함께 쓴다). */
public final class NoTransactions {

    private NoTransactions() {}

    public static PlatformTransactionManager manager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }
}
