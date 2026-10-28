package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleStatus;
import com.a207.runkk.battle.CelebrityInput;
import com.a207.runkk.battle.CelebrityRecord;
import com.a207.runkk.battle.CelebrityRecordRepository;
import com.a207.runkk.battle.LiveBattleSession;
import com.a207.runkk.battle.LiveSessionStore;
import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchStatus;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.battle.QueueRemoval;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.TrackSample;
import com.a207.runkk.battle.WeeklyRecord;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.common.Week;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * infra.battle 기본 구현(Redis 대기열·세션, JPA 저장소)을 실 PostgreSQL·Redis로 확인한다(14장 통합 사용 기반: infra.<도메인>).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("infra.battle Redis·JPA 구현")
class RedisBattleStoresIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0);

    @Autowired MatchQueue queue;
    @Autowired LiveSessionStore sessions;
    @Autowired BattleRepository battles;
    @Autowired CelebrityRecordRepository celebrities;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        jdbc.update("delete from battle_disconnection");
        jdbc.update("delete from battle_participant");
        jdbc.update("delete from battle");
        jdbc.update("delete from celebrity_record");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("RedisMatchQueue: 없을 때만 넣기, 오래된 순, 원자 배정, 지우기")
    void redisQueue() {
        assertThat(queue).isInstanceOf(RedisMatchQueue.class);
        assertThat(
                        queue.enqueue(
                                MatchTicket.open(
                                        2, Tier.GOLD, StandardDistance.KM5, NOW.plusSeconds(1))))
                .isTrue();
        assertThat(queue.enqueue(MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, NOW)))
                .isTrue();
        assertThat(queue.enqueue(MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, NOW)))
                .isFalse();
        assertThat(queue.waitingOldestFirst())
                .extracting(MatchTicket::getMemberId)
                .containsExactly(1L, 2L);
        assertThat(queue.tryAssignPair(1, 2)).isTrue();
        assertThat(queue.tryAssignPair(1, 2)).isFalse();
        assertThat(queue.findWaiting(2)).map(MatchTicket::getStatus).contains(MatchStatus.ASSIGNED);
        assertThat(queue.remove(1)).isTrue();
        assertThat(queue.findWaiting(1)).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "D-64·D-83: tryAssign은 WAITING일 때만, 그동안 취소(removeIfWaiting)는 ASSIGNED, release 뒤 다시"
                    + " 대기·취소(REMOVED), 표가 없으면 ABSENT")
    void singleAssignAndRelease() {
        queue.enqueue(MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, NOW));
        assertThat(queue.tryAssign(1)).isTrue();
        assertThat(queue.tryAssign(1)).isFalse();
        assertThat(queue.removeIfWaiting(1)).isEqualTo(QueueRemoval.ASSIGNED);
        assertThat(queue.findWaiting(1)).isPresent();
        assertThat(queue.waitingOldestFirst()).isEmpty();
        queue.release(1);
        assertThat(queue.waitingOldestFirst())
                .singleElement()
                .satisfies(
                        t -> {
                            assertThat(t.getStatus()).isEqualTo(MatchStatus.WAITING);
                            assertThat(t.getWaitingSince()).isEqualTo(NOW);
                        });
        assertThat(queue.tryAssign(2)).isFalse();
        assertThat(queue.removeIfWaiting(1)).isEqualTo(QueueRemoval.REMOVED);
        assertThat(queue.findWaiting(1)).isEmpty();
        assertThat(queue.removeIfWaiting(1)).isEqualTo(QueueRemoval.ABSENT);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("RedisLiveSessionStore: 대결마다 해시 하나 왕복, 목록·삭제")
    void redisSessions() {
        LiveBattleSession session =
                LiveBattleSession.open(
                        5L,
                        Distance.ofMeters(5_000),
                        Map.of(1L, DisplayName.of("앨리스"), 2L, DisplayName.of("밥")),
                        NOW);
        sessions.save(session);
        assertThat(sessions.load(5L).orElseThrow().snapshot()).isEqualTo(session.snapshot());
        assertThat(sessions.activeBattleIds()).containsExactly(5L);
        sessions.delete(5L);
        assertThat(sessions.activeBattleIds()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("JpaBattleRepository: 활성 확인, 시계 조회, 이력·주간 전적, 낙관적 잠금 버전")
    void battleRepositoryQueries() {
        MatchTicket a = MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, NOW);
        MatchTicket b = MatchTicket.open(2, Tier.GOLD, StandardDistance.KM5, NOW);
        a.assign();
        b.assign();
        long id =
                tx.execute(
                        s -> {
                            Battle battle = Battle.random(a, b, NOW);
                            battle.scheduleStart(NOW.plusSeconds(13));
                            return battles.save(battle).getId();
                        });
        assertThat(battles.existsActive(1)).isTrue();
        assertThat(battles.activeMemberIds(List.of(1L, 2L, 3L))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(battles.activeMemberIds(List.of())).isEmpty();
        assertThat(battles.findStartDue(NOW)).isEmpty();
        assertThat(battles.findStartDue(NOW.plusSeconds(13))).hasSize(1);

        tx.executeWithoutResult(
                s -> {
                    Battle battle = battles.findById(id).orElseThrow();
                    battle.start(NOW.plusSeconds(13));
                    battle.recordArrival(Side.member(1), NOW.plusMinutes(25));
                    battle.markDisconnected(2, NOW.plusMinutes(3));
                    battles.save(battle);
                });
        LocalDateTime endsAt = NOW.plusMinutes(25).plusSeconds(10);
        assertThat(battles.findCountdownDue(endsAt.minusSeconds(1))).isEmpty();
        assertThat(battles.findCountdownDue(endsAt)).hasSize(1);

        tx.executeWithoutResult(
                s -> {
                    Battle battle = battles.findById(id).orElseThrow();
                    battle.forfeit(2, endsAt.minusSeconds(5));
                    battles.save(battle);
                });
        Battle finished = tx.execute(s -> battles.findById(id).orElseThrow());
        assertThat(finished.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(battles.existsActive(1)).isFalse();
        assertThat(battles.activeMemberIds(List.of(1L, 2L))).isEmpty();
        DateRange today = new DateRange(NOW.toLocalDate(), NOW.toLocalDate());
        assertThat((List<Battle>) tx.execute(s -> battles.findHistory(1, today))).hasSize(1);
        assertThat(battles.weeklyRecord(1, Week.of(NOW))).isEqualTo(new WeeklyRecord(1, 0, 0));
        assertThat(battles.weeklyRecord(2, Week.of(NOW))).isEqualTo(new WeeklyRecord(0, 1, 0));
        assertThat(battles.findPendingExpired(NOW.plusDays(2))).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("JpaCelebrityRecordRepository: 구간 기록 저장·거리 필터·삭제")
    void celebrityRepository() {
        Distance fiveK = Distance.ofMeters(5_000);
        long id =
                tx.execute(
                        s ->
                                celebrities
                                        .save(
                                                CelebrityRecord.register(
                                                        new CelebrityInput(
                                                                "전설",
                                                                fiveK,
                                                                Duration.ofMinutes(14),
                                                                List.of(
                                                                        new TrackSample(
                                                                                Duration.ofMinutes(
                                                                                        7),
                                                                                Distance.ofMeters(
                                                                                        2_500)),
                                                                        new TrackSample(
                                                                                Duration.ofMinutes(
                                                                                        14),
                                                                                fiveK)),
                                                                "대회",
                                                                null),
                                                        NOW))
                                        .getId());
        assertThat(
                        (List<CelebrityRecord>)
                                tx.execute(
                                        s ->
                                                celebrities.findAll(
                                                        Optional.of(StandardDistance.KM5))))
                .hasSize(1);
        assertThat(
                        (List<CelebrityRecord>)
                                tx.execute(
                                        s ->
                                                celebrities.findAll(
                                                        Optional.of(StandardDistance.KM10))))
                .isEmpty();
        CelebrityRecord loaded = tx.execute(s -> celebrities.findById(id).orElseThrow());
        assertThat(loaded.isEstimated()).isFalse();
        assertThat(loaded.track(fiveK).arrivalTime(Distance.ofMeters(2_500)))
                .contains(Duration.ofMinutes(7));
        tx.executeWithoutResult(s -> celebrities.delete(id));
        assertThat((Optional<CelebrityRecord>) tx.execute(s -> celebrities.findById(id))).isEmpty();
    }
}
