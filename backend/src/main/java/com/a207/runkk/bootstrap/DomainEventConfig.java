package com.a207.runkk.bootstrap;

import com.a207.runkk.common.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 도메인 사건 발행기를 빈으로 등록한다(설계 6.1.13 bootstrap). */
@Configuration(proxyBeanMethods = false)
public class DomainEventConfig {

    @Bean
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringDomainEventPublisher(applicationEventPublisher);
    }
}
