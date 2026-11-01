package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 워치 세션 세대(W25, watch_session_generation): PostgreSQL(Flyway 마이그레이션 적용)에서 행이 없으면 0이고, 올릴 때마다 1씩 늘며,
 * 같은 회원을 동시에 처음 올려도 하나도 잃지 않는다(UPDATE, 없으면 INSERT … ON CONFLICT DO NOTHING, 졌으면 다시 UPDATE).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("워치 세션 세대(PostgreSQL)")
class WatchSessionGenerationPersistenceTest {

    private static final int RACERS = 8;
    private static final Instant T0 = Instant.parse("2026-11-01T06:00:00Z");

    @Autowired JdbcWatchSessionGenerations store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from watch_session_generation");
    }

    @Test
    @DisplayName("행이 없으면 0, 올릴 때마다 1씩 늘고 회원마다 따로다")
    void advance_incrementsPerMember() {
        assertThat(store.current(1)).isZero();

        store.advance(1, T0);
        store.advance(1, T0.plusSeconds(1));
        store.advance(2, T0);

        assertThat(store.current(1)).isEqualTo(2);
        assertThat(store.current(2)).isEqualTo(1);
        assertThat(store.current(3)).isZero();
    }

    @Test
    @DisplayName("같은 회원을 동시에 처음 올려도 올린 수만큼 늘어난다")
    void concurrentFirstAdvance_losesNothing() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            results.add(
                    pool.submit(
                            () -> {
                                go.await();
                                store.advance(42, T0);
                                return null;
                            }));
        }
        go.countDown();
        pool.shutdown();
        for (Future<?> f : results) {
            f.get();
        }

        assertThat(store.current(42)).isEqualTo(RACERS);
    }
}
