package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskHolder;
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

    @Autowired java.util.List<ScheduledTaskHolder> scheduledTaskHolders;

    @Value("${spring.task.scheduling.pool.size}")
    int schedulerPoolSize;

    /**
     * 시험 프로필이 꺼 둔 주기 작업 수(application-test.yml): 대결 시계 설정 전체 4개(battle-tick-enabled), 크루런 1초 시계
     * 1개(crew-tick-enabled), 런바디 진행 1개(runbody-poll-enabled).
     */
    private static final int TASKS_OFF_IN_TEST_PROFILE = 6;

    @Test
    @DisplayName("M6·L2: 비동기 수신자는 컨테이너가 관리하는 실행기를 쓰고 설정값은 application.yml에서 온다")
    void asyncExecutor_isContainerManagedBeanConfiguredFromProperties() {
        assertThat(asyncConfig.getAsyncExecutor()).isSameAs(applicationTaskExecutor);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) applicationTaskExecutor;
        assertThat(pool.getCorePoolSize()).isEqualTo(4);
        assertThat(pool.getMaxPoolSize()).isEqualTo(8);
        assertThat(pool.getThreadNamePrefix()).isEqualTo("runkk-async-");
    }

    @Test
    @DisplayName("BR-4: 스케줄러 스레드 기본값은 등록된 주기 작업 수 이상이다(1초 시계가 0시·3시 작업과 스레드를 다투지 않게)")
    void schedulerPool_coversEveryRegisteredTask() {
        int registered =
                scheduledTaskHolders.stream().mapToInt(h -> h.getScheduledTasks().size()).sum();
        int tasks = registered + TASKS_OFF_IN_TEST_PROFILE;
        assertThat(tasks).as("운영 등록 작업 수(application.yml 주석과 같다)").isEqualTo(25);
        assertThat(schedulerPoolSize).isGreaterThanOrEqualTo(tasks);
    }

    @Test
    @DisplayName("L3: 내장 Redis 포트는 @ConfigurationProperties로 받는다")
    void embeddedRedis_bindsProperties() {
        assertThat(embeddedRedis.enabled()).isTrue();
        assertThat(embeddedRedis.port()).isEqualTo(6371);
    }
}
