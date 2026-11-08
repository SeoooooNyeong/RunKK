package com.a207.runkk.infra.running;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.Tier;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.EvaluationView;
import com.a207.runkk.running.OwnedRun;
import com.a207.runkk.running.RejudgeState;
import com.a207.runkk.running.RunListRow;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecord;
import com.a207.runkk.running.RunRecordRepository;
import com.a207.runkk.running.RunSampleRow;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.SavedRun;
import com.a207.runkk.running.TotalStats;
import com.a207.runkk.running.ValidRunScore;
import com.a207.runkk.running.ValidRunStat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
            "select r.recordId, r.startedAt, r.distanceMeters, r.cardKind, r.battleId from"
                    + " RunRecord r";
    private static final String LIST_COLUMNS =
            "select r.recordId, r.memberId, r.startedAt, r.distanceMeters, r.elapsedMillis,"
                    + " r.averagePaceSeconds, r.valid, r.score, r.mode, r.cardKind, r.battleId,"
                    + " r.crewRunPostId, r.newRecordsData, r.routeShape from RunRecord r";
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
        entityManager
                .createNativeQuery("delete from run_record_tombstone where member_id = :m")
                .setParameter("m", memberId)
                .executeUpdate();
    }

    @Override
    public void delete(RunRecord r) {
        entityManager.remove(entityManager.contains(r) ? r : entityManager.merge(r));
        entityManager.flush();
    }

    @Override
    public void bury(String recordId, long memberId, LocalDateTime at) {
        entityManager
                .createNativeQuery(
                        "insert into run_record_tombstone (record_id, member_id, deleted_at)"
                                + " values (:id, :m, :at)")
                .setParameter("id", recordId)
                .setParameter("m", memberId)
                .setParameter("at", at)
                .executeUpdate();
    }

    @Override
    public boolean isBuried(String recordId) {
        return !entityManager
                .createNativeQuery(
                        "select record_id from run_record_tombstone where record_id = :id")
                .setParameter("id", recordId)
                .getResultList()
                .isEmpty();
    }

    @Override
    public List<RunListRow> listRows(long memberId, DateRange range) {
        return range(
                        entityManager.createQuery(
                                LIST_COLUMNS
                                        + " where r.memberId = :m and"
                                        + IN_RANGE
                                        + " order by r.startedAt desc",
                                Object[].class),
                        range)
                .setParameter("m", memberId)
                .getResultList()
                .stream()
                .map(row -> RunListRow.of(summary(row), (String) row[12], (String) row[13]))
                .toList();
    }

    @Override
    public Optional<RunListRow> latestListRow(long memberId) {
        return entityManager
                .createQuery(
                        LIST_COLUMNS
                                + " where r.memberId = :m order by r.startedAt desc, r.recordId"
                                + " desc",
                        Object[].class)
                .setParameter("m", memberId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(row -> RunListRow.of(summary(row), (String) row[12], (String) row[13]));
    }

    @Override
    public List<RunRecord> findMissingShape(String afterRecordId, int limit) {
        return entityManager
                .createQuery(
                        "select r from RunRecord r where r.routeShape is null"
                                + " and r.routePurged = false and r.pointCount >= 2"
                                + " and r.recordId > :after order by r.recordId",
                        RunRecord.class)
                .setParameter("after", afterRecordId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Optional<EvaluationView> evaluationOf(String recordId) {
        return entityManager
                .createQuery(
                        "select r.evaluationKind, r.evaluationTier, r.evaluationValidRuns,"
                                + " r.evaluationRunimalType from RunRecord r"
                                + " where r.recordId = :id and r.evaluationKind is not null",
                        Object[].class)
                .setParameter("id", recordId)
                .getResultList()
                .stream()
                .findFirst()
                .map(
                        row ->
                                new EvaluationView(
                                        (String) row[0],
                                        row[1] == null ? null : Tier.valueOf((String) row[1]),
                                        row[2] == null ? 0 : (Integer) row[2],
                                        (String) row[3]));
    }

    @Override
    public void noteRunimalType(String recordId, String runimalType) {
        entityManager
                .createNativeQuery(
                        "update run_record set evaluation_runimal_type = :t where record_id = :id")
                .setParameter("t", runimalType)
                .setParameter("id", recordId)
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
    public List<RunSummary> validSummariesAtLeast(
            long minMeters, LocalDateTime from, LocalDateTime to) {
        return entityManager
                .createQuery(
                        SUMMARY_COLUMNS
                                + " where r.valid = true and r.distanceMeters >= :min"
                                + " and r.startedAt >= :from and r.startedAt <= :to"
                                + " order by r.startedAt, r.recordId",
                        Object[].class)
                .setParameter("min", minMeters)
                .setParameter("from", from)
                .setParameter("to", to)
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
                                    + " coalesce(sum(r.elapsedMillis), 0), count(r) from RunRecord"
                                    + " r where r.memberId = :m and r.valid = true",
                                Object[].class)
                        .setParameter("m", memberId)
                        .getSingleResult();
        return new TotalStats(
                Distance.ofMeters(((Number) row[0]).longValue()),
                Duration.ofMillis(((Number) row[1]).longValue()),
                ((Number) row[2]).intValue());
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
                .map(JpaRunRecordRepository::savedRun)
                .toList();
    }

    @Override
    public Distance validDistanceSavedBefore(long memberId, LocalDateTime cutoff) {
        Number meters =
                entityManager
                        .createQuery(
                                "select coalesce(sum(r.distanceMeters), 0) from RunRecord r"
                                        + " where r.memberId = :m and r.valid = true"
                                        + " and r.savedAt < :cutoff",
                                Number.class)
                        .setParameter("m", memberId)
                        .setParameter("cutoff", cutoff)
                        .getSingleResult();
        return Distance.ofMeters(meters.longValue());
    }

    @Override
    public List<ValidRunScore> validRunsAfter(long memberId, int ordinal) {
        int skip = Math.max(0, ordinal);
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select r.score, r.distanceMeters from RunRecord r"
                                        + " where r.memberId = :m and r.valid = true"
                                        + " order by r.savedAt, r.recordId",
                                Object[].class)
                        .setParameter("m", memberId)
                        .setFirstResult(skip)
                        .getResultList();
        List<ValidRunScore> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            result.add(
                    new ValidRunScore(
                            skip + i + 1, (Double) row[0], Distance.ofMeters((Long) row[1])));
        }
        return result;
    }

    @Override
    public List<ValidRunStat> validRunStats(long memberId, int fromOrdinal, int toOrdinal) {
        if (fromOrdinal < 1 || toOrdinal < fromOrdinal) {
            throw new IllegalArgumentException("순번 범위 오류: " + fromOrdinal + "~" + toOrdinal);
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select r.startedAt, r.distanceMeters, r.averagePaceSeconds,"
                                        + " r.heartRateAvg from RunRecord r"
                                        + " where r.memberId = :m and r.valid = true"
                                        + " order by r.savedAt, r.recordId",
                                Object[].class)
                        .setParameter("m", memberId)
                        .setFirstResult(fromOrdinal - 1)
                        .setMaxResults(toOrdinal - fromOrdinal + 1)
                        .getResultList();
        List<ValidRunStat> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            result.add(
                    new ValidRunStat(
                            fromOrdinal + i,
                            (LocalDateTime) row[0],
                            Distance.ofMeters((Long) row[1]),
                            new Pace((Integer) row[2]),
                            (Integer) row[3]));
        }
        return result;
    }

    @Override
    public List<SavedRun> tierPendingBetween(LocalDateTime from, LocalDateTime to) {
        return entityManager
                .createQuery(
                        "select r.recordId, r.memberId, r.startedAt, r.distanceMeters, r.valid,"
                                + " r.crewRunPostId, r.savedAt from RunRecord r"
                                + " where r.valid = true and r.tierApplied = false"
                                + " and r.savedAt >= :from and r.savedAt < :to"
                                + " order by r.savedAt, r.recordId",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()
                .stream()
                .map(JpaRunRecordRepository::savedRun)
                .toList();
    }

    @Override
    public Optional<ValidRunScore> validRunScoreOf(String recordId) {
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select r.memberId, r.savedAt, r.score, r.distanceMeters from"
                                        + " RunRecord r where r.recordId = :id and r.valid = true",
                                Object[].class)
                        .setParameter("id", recordId)
                        .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        long ordinal =
                entityManager
                        .createQuery(
                                "select count(r) from RunRecord r where r.memberId = :m and"
                                        + " r.valid = true and (r.savedAt < :s or (r.savedAt = :s"
                                        + " and r.recordId <= :id))",
                                Long.class)
                        .setParameter("m", row[0])
                        .setParameter("s", row[1])
                        .setParameter("id", recordId)
                        .getSingleResult();
        return Optional.of(
                new ValidRunScore(
                        (int) ordinal, (Double) row[2], Distance.ofMeters((Long) row[3])));
    }

    @Override
    public boolean markTierApplied(String recordId, Optional<EvaluationView> evaluation) {
        // 엔티티 칼럼은 updatable = false라 조건부 갱신으로만 세운다. 행 잠금으로 리스너와 보정이 겹쳐도 한쪽만 참이다
        if (evaluation.isEmpty()) {
            return entityManager
                            .createNativeQuery(
                                    "update run_record set tier_applied = true"
                                            + " where record_id = :id and tier_applied = false")
                            .setParameter("id", recordId)
                            .executeUpdate()
                    > 0;
        }
        // 평가 표시(v0.14 D-291)를 반영 표시와 같은 갱신에 얹는다. 이미 반영했으면 둘 다 바뀌지 않는다(멱등)
        return entityManager
                        .createNativeQuery(
                                "update run_record set tier_applied = true,"
                                        + " evaluation_kind = :kind, evaluation_tier = :tier,"
                                        + " evaluation_valid_runs = :runs"
                                        + " where record_id = :id and tier_applied = false")
                        .setParameter("kind", evaluation.get().kind())
                        .setParameter("tier", evaluation.get().tier().name())
                        .setParameter("runs", evaluation.get().validRuns())
                        .setParameter("id", recordId)
                        .executeUpdate()
                > 0;
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

    private static SavedRun savedRun(Object[] row) {
        return new SavedRun(
                (String) row[0],
                (Long) row[1],
                (LocalDateTime) row[2],
                Distance.ofMeters((Long) row[3]),
                (Boolean) row[4],
                (Long) row[5],
                (LocalDateTime) row[6]);
    }

    private static RunSampleRow sample(Object[] row) {
        return RunSampleRow.of(
                (String) row[0],
                (LocalDateTime) row[1],
                Distance.ofMeters((Long) row[2]),
                (CardKind) row[3],
                (Long) row[4]);
    }

    @Override
    public List<String> rejudgeCandidates(RejudgeState state, int limit) {
        return entityManager
                .createQuery(
                        "select r.recordId from RunRecord r where r.rejudgeState = :s"
                                + " order by r.savedAt, r.recordId",
                        String.class)
                .setParameter("s", state)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Optional<RunRecord> findForUpdate(String recordId) {
        return Optional.ofNullable(
                entityManager.find(RunRecord.class, recordId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public boolean storeRejudgement(RunRecord record, RejudgeState from, RejudgeState to) {
        // 거리·시간·페이스 열은 updatable = false라 조건부 갱신으로만 쓴다(일회성 재판정, V28).
        // 점수는 무효면 NULL이다(ck_run_record_score). 형식 없는 null 바인딩을 피해 식으로 넣는다.
        boolean scored = record.getScore() != null;
        Query update =
                entityManager.createNativeQuery(
                        "update run_record set distance_meters = :d, elapsed_millis = :t,"
                                + " average_pace_seconds = :p, valid = :v,"
                                + (scored ? " score = :sc," : " score = NULL,")
                                + " excluded_meters = :xd, excluded_millis = :xt,"
                                + " rejudge_state = :to"
                                + " where record_id = :id and rejudge_state = :from");
        if (scored) {
            update.setParameter("sc", record.getScore().value());
        }
        return update.setParameter("d", record.getDistance().meters())
                        .setParameter("t", record.getElapsed().toMillis())
                        .setParameter("p", record.getAveragePace().secondsPerKm())
                        .setParameter("v", record.isValid())
                        .setParameter("xd", record.getExcludedDistance().meters())
                        .setParameter("xt", record.getExcludedElapsed().toMillis())
                        .setParameter("to", to.name())
                        .setParameter("id", record.getRecordId())
                        .setParameter("from", from.name())
                        .executeUpdate()
                > 0;
    }

    @Override
    public boolean moveRejudgeState(String recordId, RejudgeState from, RejudgeState to) {
        return entityManager
                        .createNativeQuery(
                                "update run_record set rejudge_state = :to"
                                        + " where record_id = :id and rejudge_state = :from")
                        .setParameter("to", to.name())
                        .setParameter("id", recordId)
                        .setParameter("from", from.name())
                        .executeUpdate()
                > 0;
    }
}
