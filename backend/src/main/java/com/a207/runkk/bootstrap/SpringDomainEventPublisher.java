package com.a207.runkk.bootstrap;

import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

/**
 * common.DomainEventPublisher를 Spring 사건 발행기로 구현한다(설계 5.1.1, design-review.md E2). 수신자는 발행 패키지의 사건
 * record를 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다(D-03).
 */
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent e) {
        publisher.publishEvent(e);
    }
}
