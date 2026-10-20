package com.a207.runkk.common;

/**
 * 도메인 사건 발행 포트(D-03, D-31). bootstrap.SpringDomainEventPublisher가 Spring 사건 발행기로 구현하고, 수신자는
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent e);
}
