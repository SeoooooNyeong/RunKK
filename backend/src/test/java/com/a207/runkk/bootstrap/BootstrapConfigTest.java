package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

/** 1단계 리뷰 M6(실행기 수명)·L2(실행기 설정값)·L3(내장 Redis 설정 바인딩). */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@DisplayName("bootstrap 설정")
class BootstrapConfigTest {

    @Autowired AsyncConfig asyncConfig;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    Executor applicationTaskExecutor;

    @Autowired EmbeddedRedisConfig.Properties embeddedRedis;

    @Test
    @DisplayName("M6·L2: 비동기 수신자는 컨테이너가 관리하는 실행기를 쓰고 설정값은 application.yml에서 온다")
    void asyncExecutor_isContainerManagedBeanConfiguredFromProperties() {
        assertThat(asyncConfig.getAsyncExecutor()).isSameAs(applicationTaskExecutor);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) applicationTaskExecutor;
        assertThat(pool.getCorePoolSize()).isEqualTo(2);
        assertThat(pool.getMaxPoolSize()).isEqualTo(4);
        assertThat(pool.getThreadNamePrefix()).isEqualTo("runkk-async-");
    }

    @Test
    @DisplayName("L3: 내장 Redis 포트는 @ConfigurationProperties로 받는다")
    void embeddedRedis_bindsProperties() {
        assertThat(embeddedRedis.enabled()).isTrue();
        assertThat(embeddedRedis.port()).isEqualTo(6371);
    }
}
