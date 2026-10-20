package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.DomainEventPublisher;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;

class SpringDomainEventPublisherTest {

    record SampleEvent(LocalDateTime occurredAt) implements DomainEvent {}

    @Test
    void publish_delegatesToSpringEventPublisher() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        DomainEventPublisher publisher = new SpringDomainEventPublisher(spring);
        SampleEvent event = new SampleEvent(LocalDateTime.of(2026, 10, 20, 9, 0));

        publisher.publish(event);

        verify(spring).publishEvent(event);
    }

    @Test
    void domainEventConfig_wiresPublisher() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        assertThat(new DomainEventConfig().domainEventPublisher(spring))
                .isInstanceOf(SpringDomainEventPublisher.class);
    }

    @Test
    void asyncConfig_providesBoundedExecutor() {
        TaskExecutor executor = new AsyncConfig().getAsyncExecutor();
        assertThat(executor).isNotNull();
        assertThat(new AsyncConfig().getAsyncUncaughtExceptionHandler()).isNotNull();
    }
}
