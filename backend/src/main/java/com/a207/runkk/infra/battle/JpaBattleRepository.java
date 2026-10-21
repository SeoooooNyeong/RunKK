package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleStatus;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.battle.WeeklyRecord;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Week;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * battle.BattleRepository의 JPA 구현(설계 5.1.1 infra.battle).
 * battle·battle_participant·battle_disconnection 테이블만 읽고 쓴다(D-32). 시계 조회는 상태·시각 열 인덱스로 찾는다.
 */
@Repository
public class JpaBattleRepository implements BattleRepository {

    private static final List<BattleStatus> RUNNING =
            List.of(BattleStatus.IN_PROGRESS, BattleStatus.COUNTDOWN);
    private static final List<BattleStatus> HISTORY =
            List.of(BattleStatus.FINISHED, BattleStatus.RESULT_PENDING);

    private final EntityManager entityManager;

    public JpaBattleRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Battle save(Battle battle) {
        if (battle.getId() == null) {
            entityManager.persist(battle);
            return battle;
        }
        return entityManager.contains(battle) ? battle : entityManager.merge(battle);
    }

    @Override
    public Optional<Battle> findById(long id) {
        return Optional.ofNullable(entityManager.find(Battle.class, id));
    }

    @Override
    public boolean existsActive(long memberId) {
        return entityManager
                        .createQuery(
                                "select count(b) from Battle b join b.participants p"
                                        + " where p.memberId = :member and b.status in :active",
                                Long.class)
                        .setParameter("member", memberId)
                        .setParameter("active", List.copyOf(BattleStatus.active()))
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<Battle> findStartDue(LocalDateTime now) {
        return entityManager
                .createQuery(
                        "select b from Battle b where b.status = :waiting and b.startAt <= :now",
                        Battle.class)
                .setParameter("waiting", BattleStatus.WAITING_START)
                .setParameter("now", now)
                .getResultList();
    }

    @Override
    public List<Battle> findCountdownDue(LocalDateTime now) {
        return entityManager
                .createQuery(
                        "select b from Battle b where (b.status = :countdown and b.countdownEndsAt"
                                + " <= :now) or (b.status in :running and b.maxEndsAt <= :now)",
                        Battle.class)
                .setParameter("countdown", BattleStatus.COUNTDOWN)
                .setParameter("running", RUNNING)
                .setParameter("now", now)
                .getResultList();
    }

    @Override
    public List<Battle> findOpponentArrivalDue(LocalDateTime now) {
        return entityManager
                .createQuery(
                        "select b from Battle b where b.status in :running and b.opponentArrivesAt"
                                + " <= :now and b.opponentArrivedAt is null",
                        Battle.class)
                .setParameter("running", RUNNING)
                .setParameter("now", now)
                .getResultList();
    }

    @Override
    public List<Battle> findPendingExpired(LocalDateTime cutoff) {
        return entityManager
                .createQuery(
                        "select b from Battle b where b.status = :pending"
                                + " and coalesce(b.countdownEndsAt, b.closedAt) <= :cutoff",
                        Battle.class)
                .setParameter("pending", BattleStatus.RESULT_PENDING)
                .setParameter("cutoff", cutoff)
                .getResultList();
    }

    @Override
    public List<Battle> findHistory(long memberId, DateRange range) {
        return entityManager
                .createQuery(
                        "select b from Battle b join b.participants p where p.memberId = :member"
                                + " and b.status in :history and b.createdAt >= :from"
                                + " and b.createdAt < :to order by b.id desc",
                        Battle.class)
                .setParameter("history", HISTORY)
                .setParameter("from", range.start().atStartOfDay())
                .setParameter("to", range.end().plusDays(1).atStartOfDay())
                .setParameter("member", memberId)
                .getResultList();
    }

    @Override
    public WeeklyRecord weeklyRecord(long memberId, Week week) {
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select p.verdict, count(p) from Battle b join b.participants p"
                                        + " where p.memberId = :member and b.status = :finished"
                                        + " and b.createdAt >= :from and b.createdAt < :to"
                                        + " and p.verdict is not null group by p.verdict",
                                Object[].class)
                        .setParameter("member", memberId)
                        .setParameter("finished", BattleStatus.FINISHED)
                        .setParameter("from", week.monday().atStartOfDay())
                        .setParameter("to", week.closesAt())
                        .getResultList();
        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        rows.forEach(row -> counts.put((Verdict) row[0], ((Long) row[1]).intValue()));
        return new WeeklyRecord(
                counts.getOrDefault(Verdict.WIN, 0),
                counts.getOrDefault(Verdict.LOSE, 0),
                counts.getOrDefault(Verdict.DRAW, 0));
    }
}
