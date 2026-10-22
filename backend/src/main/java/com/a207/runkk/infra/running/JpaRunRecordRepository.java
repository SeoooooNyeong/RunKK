package com.a207.runkk.infra.running;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.OwnedRun;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecord;
import com.a207.runkk.running.RunRecordRepository;
import com.a207.runkk.running.RunSampleRow;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.SavedRun;
import com.a207.runkk.running.TotalStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Repository;

/**
 * running.RunRecordRepository의 JPA 구현(설계 5.1.1 infra.running). run_record 테이블만 읽고 쓴다(D-32). 목록·통계
 * 조회는 필요한 열만 고르는 투영 쿼리로 해 위치·심박 바이너리 열을 읽지 않는다(6.5).
 */
@Repository
public class JpaRunRecordRepository implements RunRecordRepository {

    private static final String SUMMARY_COLUMNS =
            "select r.recordId, r.memberId, r.startedAt, r.distanceMeters, r.elapsedMillis,"
                    + " r.averagePaceSeconds, r.valid, r.score, r.mode, r.cardKind, r.battleId,"
                    + " r.crewRunPostId from RunRecord r";
    private static final String SAMPLE_COLUMNS =
            "select r.recordId, r.startedAt, r.distanceMeters from RunRecord r";
    private static final String IN_RANGE = " r.startedAt >= :from and r.startedAt < :to";

    private final EntityManager entityManager;

    public JpaRunRecordRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean exists(String recordId) {
        return entityManager
                        .createQuery(
                                "select count(r) from RunRecord r where r.recordId = :id",
                                Long.class)
                        .setParameter("id", recordId)
                        .getSingleResult()
                > 0;
    }

    @Override
    public Optional<RunRecord> findById(String recordId) {
        return Optional.ofNullable(entityManager.find(RunRecord.class, recordId));
    }

    @Override
    public RunRecord save(RunRecord record) {
        if (!entityManager.contains(record)) {
            entityManager.persist(record);
        }
        return record;
    }

    @Override
    public List<RunRecord> findByMember(long memberId) {
        return entityManager
                .createQuery("select r from RunRecord r where r.memberId = :m", RunRecord.class)
                .setParameter("m", memberId)
                .getResultList();
    }

    @Override
    public List<RunRecord> recentValid(long memberId, int limit) {
        return entityManager
                .createQuery(
                        "select r from RunRecord r where r.memberId = :m and r.valid = true"
                                + " order by r.startedAt desc",
                        RunRecord.class)
                .setParameter("m", memberId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<RunRecord> findByMemberBetween(long memberId, DateRange range) {
        return range(
                        entityManager.createQuery(
                                "select r from RunRecord r where r.memberId = :m and"
                                        + IN_RANGE
                                        + " order by r.startedAt desc",
                                RunRecord.class),
                        range)
                .setParameter("m", memberId)
                .getResultList();
    }

    @Override
    public int countValid(long memberId) {
        return entityManager
                .createQuery(
                        "select count(r) from RunRecord r where r.memberId = :m and r.valid = true",
                        Long.class)
                .setParameter("m", memberId)
                .getSingleResult()
                .intValue();
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from RunRecord r where r.memberId = :m")
                .setParameter("m", memberId)
                .executeUpdate();
    }

    @Override
    public Optional<RunSummary> findSummary(String recordId) {
        return entityManager
                .createQuery(SUMMARY_COLUMNS + " where r.recordId = :id", Object[].class)
                .setParameter("id", recordId)
                .getResultList()
                .stream()
                .findFirst()
                .map(JpaRunRecordRepository::summary);
    }

    @Override
    public List<RunSummary> summariesBetween(long memberId, DateRange range) {
        return range(
                        entityManager.createQuery(
                                SUMMARY_COLUMNS
                                        + " where r.memberId = :m and"
                                        + IN_RANGE
                                        + " order by r.startedAt desc",
                                Object[].class),
                        range)
                .setParameter("m", memberId)
                .getResultList()
                .stream()
                .map(JpaRunRecordRepository::summary)
                .toList();
    }

    @Override
    public List<RunSummary> recentValidSummaries(long memberId, int limit) {
        return entityManager
                .createQuery(
                        SUMMARY_COLUMNS
                                + " where r.memberId = :m and r.valid = true order by r.startedAt"
                                + " desc",
                        Object[].class)
                .setParameter("m", memberId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(JpaRunRecordRepository::summary)
                .toList();
    }

    @Override
    public List<RunSampleRow> recentValidSamples(long memberId, int limit) {
        return entityManager
                .createQuery(
                        SAMPLE_COLUMNS
                                + " where r.memberId = :m and r.valid = true order by r.startedAt"
                                + " desc",
                        Object[].class)
                .setParameter("m", memberId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(JpaRunRecordRepository::sample)
                .toList();
    }

    @Override
    public List<RunSampleRow> validSamplesBetween(long memberId, DateRange range) {
        return range(
                        entityManager.createQuery(
                                SAMPLE_COLUMNS
                                        + " where r.memberId = :m and r.valid = true and"
                                        + IN_RANGE
                                        + " order by r.startedAt desc",
                                Object[].class),
                        range)
                .setParameter("m", memberId)
                .getResultList()
                .stream()
                .map(JpaRunRecordRepository::sample)
                .toList();
    }

    @Override
    public Map<LocalDate, DayStats> dailyStats(long memberId, DateRange range) {
        List<Object[]> rows =
                range(
                                entityManager.createQuery(
                                        "select r.startedAt, r.distanceMeters, r.elapsedMillis"
                                                + " from RunRecord r where r.memberId = :m"
                                                + " and r.valid = true and"
                                                + IN_RANGE,
                                        Object[].class),
                                range)
                        .setParameter("m", memberId)
                        .getResultList();
        Map<LocalDate, DayStats> result = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDate day = ((LocalDateTime) row[0]).toLocalDate();
            DayStats one =
                    new DayStats(
                            day,
                            Distance.ofMeters((Long) row[1]),
                            Duration.ofMillis((Long) row[2]),
                            1);
            result.merge(
                    day,
                    one,
                    (a, b) ->
                            new DayStats(
                                    day,
                                    a.distance().plus(b.distance()),
                                    a.elapsed().plus(b.elapsed()),
                                    a.count() + b.count()));
        }
        return result;
    }

    @Override
    public Map<LocalDate, Distance> dailyDistances(Collection<Long> memberIds, DateRange range) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows =
                range(
                                entityManager.createQuery(
                                        "select r.startedAt, r.distanceMeters from RunRecord r"
                                                + " where r.memberId in :ids and r.valid = true and"
                                                + IN_RANGE,
                                        Object[].class),
                                range)
                        .setParameter("ids", Set.copyOf(memberIds))
                        .getResultList();
        Map<LocalDate, Distance> result = new TreeMap<>();
        for (Object[] row : rows) {
            result.merge(
                    ((LocalDateTime) row[0]).toLocalDate(),
                    Distance.ofMeters((Long) row[1]),
                    Distance::plus);
        }
        return result;
    }

    @Override
    public TotalStats totals(long memberId) {
        Object[] row =
                entityManager
                        .createQuery(
                                "select coalesce(sum(r.distanceMeters), 0),"
                                    + " coalesce(sum(r.elapsedMillis), 0), count(r),"
                                    + " coalesce(sum(case when r.valid = true then r.distanceMeters"
                                    + " else 0 end), 0) from RunRecord r where r.memberId = :m",
                                Object[].class)
                        .setParameter("m", memberId)
                        .getSingleResult();
        return new TotalStats(
                Distance.ofMeters(((Number) row[0]).longValue()),
                Duration.ofMillis(((Number) row[1]).longValue()),
                ((Number) row[2]).intValue(),
                Distance.ofMeters(((Number) row[3]).longValue()));
    }

    @Override
    public Map<Long, Distance> distancesByMember(Collection<Long> memberIds, DateRange range) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows =
                range(
                                entityManager.createQuery(
                                        "select r.memberId, sum(r.distanceMeters) from RunRecord r"
                                                + " where r.memberId in :ids and r.valid = true and"
                                                + IN_RANGE
                                                + " group by r.memberId",
                                        Object[].class),
                                range)
                        .setParameter("ids", Set.copyOf(memberIds))
                        .getResultList();
        Map<Long, Distance> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], Distance.ofMeters(((Number) row[1]).longValue()));
        }
        return result;
    }

    @Override
    public List<OwnedRun> validRunsReaching(Distance d, Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "select r.recordId, r.memberId from RunRecord r where r.memberId in :ids"
                                + " and r.valid = true and r.distanceMeters >= :d"
                                + " and r.routePurged = false and r.pointCount > 0",
                        Object[].class)
                .setParameter("ids", Set.copyOf(ownerIds))
                .setParameter("d", d.meters())
                .getResultList()
                .stream()
                .map(row -> new OwnedRun((String) row[0], (Long) row[1]))
                .toList();
    }

    @Override
    public List<SavedRun> savedBetween(LocalDateTime from, LocalDateTime to) {
        return entityManager
                .createQuery(
                        "select r.recordId, r.memberId, r.startedAt, r.distanceMeters, r.valid,"
                                + " r.crewRunPostId, r.savedAt from RunRecord r"
                                + " where r.savedAt >= :from and r.savedAt < :to"
                                + " order by r.savedAt, r.recordId",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()
                .stream()
                .map(
                        row ->
                                new SavedRun(
                                        (String) row[0],
                                        (Long) row[1],
                                        (LocalDateTime) row[2],
                                        Distance.ofMeters((Long) row[3]),
                                        (Boolean) row[4],
                                        (Long) row[5],
                                        (LocalDateTime) row[6]))
                .toList();
    }

    private static <T> TypedQuery<T> range(TypedQuery<T> query, DateRange range) {
        return query.setParameter("from", range.start().atStartOfDay())
                .setParameter("to", range.end().plusDays(1).atStartOfDay());
    }

    private static RunSummary summary(Object[] row) {
        return new RunSummary(
                (String) row[0],
                (Long) row[1],
                (LocalDateTime) row[2],
                Distance.ofMeters((Long) row[3]),
                Duration.ofMillis((Long) row[4]),
                new Pace((Integer) row[5]),
                (Boolean) row[6],
                (Double) row[7],
                (RunMode) row[8],
                (CardKind) row[9],
                (Long) row[10],
                (Long) row[11]);
    }

    private static RunSampleRow sample(Object[] row) {
        return new RunSampleRow(
                (String) row[0], (LocalDateTime) row[1], Distance.ofMeters((Long) row[2]));
    }
}
