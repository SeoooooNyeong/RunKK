package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.battle.LiveBattleSession;
import com.a207.runkk.battle.LiveSessionStore;
import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchStatus;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D-07 PostgreSQL 대안(runkk.queue=jdbc): JdbcMatchQueue(기본 키 중복 방지, FOR UPDATE SKIP LOCKED 짝 배정)와
 * JdbcLiveSessionStore가 Redis 구현과 같은 계약을 지키는지 실 PostgreSQL로 확인한다(5.5 장애: Redis 전환).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class, properties = "runkk.queue=jdbc")
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("D-07 PostgreSQL 대기열·대결 세션")
class JdbcBattleStoresIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0);
    private static final int RIVALS = 8;

    @Autowired MatchQueue queue;
    @Autowired LiveSessionStore sessions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from match_ticket");
        jdbc.update("delete from live_battle_session");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("구현이 JDBC로 바뀌고, 없을 때만 넣기·오래된 순·지우기를 지킨다")
    void queueContract() {
        assertThat(queue).isInstanceOf(JdbcMatchQueue.class);
        assertThat(sessions).isInstanceOf(JdbcLiveSessionStore.class);
        assertThat(
                        queue.enqueue(
                                MatchTicket.open(
                                        2, Tier.GOLD, StandardDistance.KM5, NOW.plusSeconds(1))))
                .isTrue();
        assertThat(queue.enqueue(MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, NOW)))
                .isTrue();
        assertThat(queue.enqueue(MatchTicket.open(1, Tier.SILVER, StandardDistance.KM3, NOW)))
                .isFalse();
        assertThat(queue.waitingOldestFirst())
                .extracting(MatchTicket::getMemberId)
                .containsExactly(1L, 2L);
        assertThat(queue.tryAssignPair(1, 2)).isTrue();
        assertThat(queue.findWaiting(1)).map(MatchTicket::getStatus).contains(MatchStatus.ASSIGNED);
        assertThat(queue.waitingOldestFirst()).isEmpty();
        assertThat(queue.tryAssignPair(1, 2)).isFalse();
        assertThat(queue.remove(1)).isTrue();
        assertThat(queue.remove(1)).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("같은 대기자를 동시에 노려도 한 번만 배정된다(SKIP LOCKED)")
    void concurrentAssign_onlyOnce() throws Exception {
        for (long id = 1; id <= RIVALS + 1; id++) {
            queue.enqueue(MatchTicket.open(id, Tier.GOLD, StandardDistance.KM5, NOW));
        }
        ExecutorService pool = Executors.newFixedThreadPool(RIVALS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> tries = new ArrayList<>();
        for (long other = 2; other <= RIVALS + 1; other++) {
            long partner = other;
            tries.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return queue.tryAssignPair(1, partner);
                            }));
        }
        go.countDown();
        int assigned = 0;
        for (Future<Boolean> f : tries) {
            assigned += f.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();
        assertThat(assigned).isEqualTo(1);
        Integer assignedRows =
                jdbc.queryForObject(
                        "select count(*) from match_ticket where status = 'ASSIGNED'",
                        Integer.class);
        assertThat(assignedRows).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("대결 세션 저장·갱신·삭제·목록")
    void sessionStore() {
        LiveBattleSession session =
                LiveBattleSession.open(
                        7L,
                        Distance.ofMeters(3_000),
                        Map.of(1L, DisplayName.of("앨리스"), 2L, DisplayName.of("밥")),
                        NOW);
        sessions.save(session);
        session.onProgress(
                1L, new ProgressReport(Distance.ofMeters(100), Duration.ofSeconds(30), NOW), NOW);
        sessions.save(session);
        LiveBattleSession loaded = sessions.load(7L).orElseThrow();
        assertThat(loaded.snapshot()).isEqualTo(session.snapshot());
        assertThat(sessions.activeBattleIds()).containsExactly(7L);
        sessions.delete(7L);
        assertThat(sessions.load(7L)).isEmpty();
    }
}
