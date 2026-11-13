package com.a207.runkk.infra.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.GoalHistoryRow;
import com.a207.runkk.crew.RaceContributionKey;
import com.a207.runkk.crew.WeeklyRace;
import com.a207.runkk.crew.WeeklyRaceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * crew.WeeklyRaceRepository의 JPA 구현(설계 5.1.1 infra.crew, 6.2 A-12, D-105).
 * weekly_race·race_contribution만 읽고 쓴다. 주 행이 없으면 먼저 만들고(직전 목표를 이어 쓴다), 기여 행 삽입이 성공했을 때만 합계를 원자적으로
 * 늘린다. 달성은 조건부 UPDATE 한 번이다. 메모리에서 읽고-더하고-쓰지 않는다(5.3, NFR-034).
 */
@Repository
public class JpaWeeklyRaceRepository implements WeeklyRaceRepository {

    /** 주 행이 없으면 만들고 그 앞 주의 가장 최근 목표를 이어 쓴다(FR-138). */
    private static final String CREATE_WEEK =
            "insert into weekly_race (crew_id, week_start, total_meters, goal_meters)"
                    + " values (:crew, :week, 0, (select w.goal_meters from weekly_race w"
                    + " where w.crew_id = :crew and w.week_start < :week"
                    + " and w.goal_meters is not null order by w.week_start desc limit 1))"
                    + " on conflict do nothing";

    private final EntityManager entityManager;

    public JpaWeeklyRaceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private void createWeek(long crewId, Week week) {
        entityManager
                .createNativeQuery(CREATE_WEEK)
                .setParameter("crew", crewId)
                .setParameter("week", week.monday())
                .executeUpdate();
    }

    @Override
    @Transactional
    public boolean addAtomically(RaceContributionKey key, Distance d) {
        createWeek(key.crewId(), key.week());
        int inserted =
                entityManager
                        .createNativeQuery(
                                "insert into race_contribution"
                                        + " (crew_id, week_start, member_id, record_id, meters)"
                                        + " values (:crew, :week, :member, :record, :meters)"
                                        + " on conflict do nothing")
                        .setParameter("crew", key.crewId())
                        .setParameter("week", key.week().monday())
                        .setParameter("member", key.memberId())
                        .setParameter("record", key.recordId())
                        .setParameter("meters", d.meters())
                        .executeUpdate();
        if (inserted == 0) {
            return false;
        }
        entityManager
                .createNativeQuery(
                        "update weekly_race set total_meters = total_meters + :meters"
                                + " where crew_id = :crew and week_start = :week")
                .setParameter("meters", d.meters())
                .setParameter("crew", key.crewId())
                .setParameter("week", key.week().monday())
                .executeUpdate();
        return true;
    }

    @Override
    public Optional<WeeklyRace> find(long crewId, Week week) {
        // 같은 트랜잭션에서 SQL로 바꾼 목표·달성 일시를 읽도록 엔티티가 아니라 SQL로 읽는다(영속성 문맥의 낡은 값 방지)
        List<?> races =
                entityManager
                        .createNativeQuery(
                                "select goal_meters, goal_set_by, achieved_at from weekly_race"
                                        + " where crew_id = :crew and week_start = :week")
                        .setParameter("crew", crewId)
                        .setParameter("week", week.monday())
                        .getResultList();
        List<WeeklyRaceRows.Contribution> rows =
                entityManager
                        .createQuery(
                                "select c from RaceContributionRow c where c.crewId = :crew"
                                        + " and c.weekStart = :week order by c.id",
                                WeeklyRaceRows.Contribution.class)
                        .setParameter("crew", crewId)
                        .setParameter("week", week.monday())
                        .getResultList();
        if (races.isEmpty() && rows.isEmpty()) {
            return Optional.empty();
        }
        WeeklyRace race =
                races.isEmpty()
                        ? WeeklyRace.empty(crewId, week)
                        : restore(crewId, week, races.get(0));
        for (WeeklyRaceRows.Contribution row : rows) {
            Distance meters = Distance.ofMeters(row.meters());
            if (row.memberId() == null) {
                race.addAnonymized(row.recordId(), meters);
            } else {
                race.add(row.memberId(), row.recordId(), meters);
            }
        }
        return Optional.of(race);
    }

    private static WeeklyRace restore(long crewId, Week week, Object row) {
        Object[] columns = (Object[]) row;
        Long goal = columns[0] == null ? null : ((Number) columns[0]).longValue();
        Long setter = columns[1] == null ? null : ((Number) columns[1]).longValue();
        return WeeklyRace.restore(
                crewId,
                week,
                new WeeklyRace.GoalState(
                        goal == null ? null : Distance.ofMeters(goal),
                        setter,
                        toLocalDateTime(columns[2])));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    @Override
    @Transactional
    public WeeklyRace findOrCreate(long crewId, Week week) {
        createWeek(crewId, week);
        return find(crewId, week).orElseThrow();
    }

    @Override
    @Transactional
    public void saveGoal(WeeklyRace race) {
        entityManager
                .createNativeQuery(
                        "update weekly_race set goal_meters = :goal, goal_set_by = :setter"
                                + " where crew_id = :crew and week_start = :week")
                .setParameter("goal", race.goal().map(Distance::meters).orElse(null))
                .setParameter("setter", race.goalSetBy().orElse(null))
                .setParameter("crew", race.crewId())
                .setParameter("week", race.week().monday())
                .executeUpdate();
    }

    @Override
    @Transactional
    public boolean markAchievedIfReached(long crewId, Week week, LocalDateTime now) {
        return entityManager
                        .createNativeQuery(
                                "update weekly_race set achieved_at = :now where crew_id = :crew"
                                        + " and week_start = :week and achieved_at is null"
                                        + " and goal_meters is not null"
                                        + " and total_meters >= goal_meters")
                        .setParameter("now", now)
                        .setParameter("crew", crewId)
                        .setParameter("week", week.monday())
                        .executeUpdate()
                == 1;
    }

    @Override
    public Optional<Distance> latestGoalBefore(long crewId, Week week) {
        List<?> goals =
                entityManager
                        .createNativeQuery(
                                "select goal_meters from weekly_race where crew_id = :crew"
                                        + " and week_start < :week and goal_meters is not null"
                                        + " order by week_start desc limit 1")
                        .setParameter("crew", crewId)
                        .setParameter("week", week.monday())
                        .getResultList();
        return goals.stream().findFirst().map(g -> Distance.ofMeters(((Number) g).longValue()));
    }

    @Override
    public Distance contributionOf(String recordId) {
        Number meters =
                (Number)
                        entityManager
                                .createQuery(
                                        "select coalesce(sum(c.meters), 0) from RaceContributionRow"
                                                + " c where c.recordId = :record")
                                .setParameter("record", recordId)
                                .getSingleResult();
        return Distance.ofMeters(meters.longValue());
    }

    @Override
    public Optional<Long> contributedCrewOf(String recordId) {
        List<?> crews =
                entityManager
                        .createQuery(
                                "select c.crewId from RaceContributionRow c where c.recordId ="
                                        + " :record order by c.crewId")
                        .setParameter("record", recordId)
                        .setMaxResults(1)
                        .getResultList();
        return crews.stream().findFirst().map(id -> ((Number) id).longValue());
    }

    @Override
    public void deleteByCrew(long crewId) {
        entityManager
                .createQuery("delete from RaceContributionRow c where c.crewId = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
        entityManager
                .createQuery("delete from WeeklyRaceRow r where r.crewId = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
    }

    @Override
    public Map<Long, Distance> totalsByMember(long crewId, List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select c.memberId, sum(c.meters) from RaceContributionRow c"
                                        + " where c.crewId = :crew and c.memberId in :members"
                                        + " group by c.memberId",
                                Object[].class)
                        .setParameter("crew", crewId)
                        .setParameter("members", memberIds)
                        .getResultList();
        Map<Long, Distance> totals = new HashMap<>();
        for (Object[] row : rows) {
            totals.put(
                    ((Number) row[0]).longValue(),
                    Distance.ofMeters(((Number) row[1]).longValue()));
        }
        return totals;
    }

    @Override
    public List<GoalHistoryRow> goalHistory(long crewId, LocalDate before, int limit) {
        List<Object[]> weeks =
                entityManager
                        .createQuery(
                                "select r.weekStart, r.goalMeters, r.totalMeters, r.achievedAt"
                                        + " from WeeklyRaceRow r where r.crewId = :crew"
                                        + " and r.goalMeters is not null and r.weekStart < :before"
                                        + " order by r.weekStart desc",
                                Object[].class)
                        .setParameter("crew", crewId)
                        .setParameter("before", before)
                        .setMaxResults(limit)
                        .getResultList();
        if (weeks.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, Integer> participants =
                participantsOf(crewId, weeks.stream().map(w -> (LocalDate) w[0]).toList());
        return weeks.stream()
                .map(
                        w ->
                                new GoalHistoryRow(
                                        (LocalDate) w[0],
                                        Distance.ofMeters(((Number) w[1]).longValue()),
                                        Distance.ofMeters(((Number) w[2]).longValue()),
                                        w[3] != null,
                                        participants.getOrDefault((LocalDate) w[0], 0)))
                .toList();
    }

    /** 주마다 기여 거리 합이 0보다 큰 기여자 수. 익명 기여(member_id null)는 한 묶음이다. */
    private Map<LocalDate, Integer> participantsOf(long crewId, List<LocalDate> weeks) {
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select c.weekStart, c.memberId, sum(c.meters)"
                                        + " from RaceContributionRow c where c.crewId = :crew"
                                        + " and c.weekStart in :weeks"
                                        + " group by c.weekStart, c.memberId",
                                Object[].class)
                        .setParameter("crew", crewId)
                        .setParameter("weeks", weeks)
                        .getResultList();
        Map<LocalDate, Integer> counts = new HashMap<>();
        rows.stream()
                .filter(r -> ((Number) Objects.requireNonNull(r[2])).longValue() > 0)
                .forEach(r -> counts.merge((LocalDate) r[0], 1, Integer::sum));
        return counts;
    }

    @Override
    public void anonymizeMember(long memberId) {
        entityManager
                .createQuery(
                        "update RaceContributionRow c set c.memberId = null where c.memberId ="
                                + " :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
