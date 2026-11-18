package com.a207.runkk.running;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/** 시험용 인메모리 running 저장소 3개(설계 13.1). 통계 조회도 같은 의미로 메모리에서 계산한다. */
public final class InMemoryRunningRepositories {

    private InMemoryRunningRepositories() {}

    /** 인메모리 러닝 기록 저장소. */
    public static class Records implements RunRecordRepository {
        private final Map<String, RunRecord> rows = new LinkedHashMap<>();

        @Override
        public boolean exists(String recordId) {
            return rows.containsKey(recordId);
        }

        @Override
        public Optional<RunRecord> findById(String recordId) {
            return Optional.ofNullable(rows.get(recordId));
        }

        @Override
        public RunRecord save(RunRecord record) {
            rows.put(record.getRecordId(), record);
            return record;
        }

        @Override
        public List<RunRecord> findByMember(long memberId) {
            return of(memberId).toList();
        }

        @Override
        public List<RunRecord> recentValid(long memberId, int limit) {
            return newest(of(memberId).filter(RunRecord::isValid)).limit(limit).toList();
        }

        @Override
        public List<RunRecord> findByMemberBetween(long memberId, DateRange range) {
            return newest(of(memberId).filter(r -> in(r, range))).toList();
        }

        @Override
        public int countValid(long memberId) {
            return (int) of(memberId).filter(RunRecord::isValid).count();
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.values().removeIf(r -> r.getMemberId() == memberId);
        }

        @Override
        public Optional<RunSummary> findSummary(String recordId) {
            return findById(recordId).map(RunRecord::summary);
        }

        @Override
        public Optional<com.a207.runkk.common.AvatarLook> findLook(String recordId) {
            return findById(recordId).flatMap(RunRecord::look);
        }

        @Override
        public List<RunSummary> findSummaries(Collection<String> recordIds) {
            return recordIds.stream()
                    .distinct()
                    .map(this::findById)
                    .flatMap(Optional::stream)
                    .map(RunRecord::summary)
                    .toList();
        }

        @Override
        public List<RunSummary> summariesBetween(long memberId, DateRange range) {
            return findByMemberBetween(memberId, range).stream().map(RunRecord::summary).toList();
        }

        @Override
        public List<RunSummary> validSummariesAtLeast(
                long minMeters, java.time.LocalDateTime from, java.time.LocalDateTime to) {
            return rows.values().stream()
                    .filter(RunRecord::isValid)
                    .filter(r -> r.getDistance().meters() >= minMeters)
                    .filter(r -> !r.getStartedAt().isBefore(from) && !r.getStartedAt().isAfter(to))
                    .sorted(Comparator.comparing(RunRecord::getStartedAt))
                    .map(RunRecord::summary)
                    .toList();
        }

        @Override
        public List<RunSummary> recentValidSummaries(long memberId, int limit) {
            return recentValid(memberId, limit).stream().map(RunRecord::summary).toList();
        }

        @Override
        public List<RunSampleRow> recentValidSamples(long memberId, int limit) {
            return recentValid(memberId, limit).stream().map(Records::sample).toList();
        }

        @Override
        public List<RunSampleRow> validSamplesBetween(long memberId, DateRange range) {
            return findByMemberBetween(memberId, range).stream()
                    .filter(RunRecord::isValid)
                    .map(Records::sample)
                    .toList();
        }

        @Override
        public Map<LocalDate, DayStats> dailyStats(long memberId, DateRange range) {
            Map<LocalDate, DayStats> result = new TreeMap<>();
            of(memberId)
                    .filter(RunRecord::isValid)
                    .filter(r -> in(r, range))
                    .forEach(
                            r ->
                                    result.merge(
                                            r.getStartedAt().toLocalDate(),
                                            new DayStats(
                                                    r.getStartedAt().toLocalDate(),
                                                    r.getDistance(),
                                                    r.getElapsed(),
                                                    1),
                                            (a, b) ->
                                                    new DayStats(
                                                            a.date(),
                                                            a.distance().plus(b.distance()),
                                                            a.elapsed().plus(b.elapsed()),
                                                            a.count() + b.count())));
            return result;
        }

        @Override
        public Map<LocalDate, Distance> dailyDistances(
                Collection<Long> memberIds, DateRange range) {
            Map<LocalDate, Distance> result = new TreeMap<>();
            rows.values().stream()
                    .filter(r -> memberIds.contains(r.getMemberId()))
                    .filter(RunRecord::isValid)
                    .filter(r -> in(r, range))
                    .forEach(
                            r ->
                                    result.merge(
                                            r.getStartedAt().toLocalDate(),
                                            r.getDistance(),
                                            Distance::plus));
            return result;
        }

        @Override
        public TotalStats totals(long memberId) {
            List<RunRecord> all =
                    findByMember(memberId).stream().filter(RunRecord::isValid).toList();
            return new TotalStats(
                    all.stream().map(RunRecord::getDistance).reduce(Distance.ZERO, Distance::plus),
                    all.stream().map(RunRecord::getElapsed).reduce(Duration.ZERO, Duration::plus),
                    all.size());
        }

        @Override
        public Map<Long, Distance> distancesByMember(Collection<Long> memberIds, DateRange range) {
            Map<Long, Distance> result = new HashMap<>();
            rows.values().stream()
                    .filter(r -> memberIds.contains(r.getMemberId()))
                    .filter(RunRecord::isValid)
                    .filter(r -> in(r, range))
                    .forEach(r -> result.merge(r.getMemberId(), r.getDistance(), Distance::plus));
            return result;
        }

        @Override
        public List<OwnedRun> validRunsReaching(Distance d, Collection<Long> ownerIds) {
            return rows.values().stream()
                    .filter(r -> ownerIds.contains(r.getMemberId()))
                    .filter(RunRecord::isValid)
                    .filter(r -> r.getDistance().isAtLeast(d))
                    .filter(RunRecord::hasRoute)
                    .map(r -> new OwnedRun(r.getRecordId(), r.getMemberId()))
                    .toList();
        }

        @Override
        public List<SavedRun> savedBetween(
                java.time.LocalDateTime from, java.time.LocalDateTime to) {
            return rows.values().stream()
                    .filter(r -> r.getSavedAt() != null)
                    .filter(r -> !r.getSavedAt().isBefore(from) && r.getSavedAt().isBefore(to))
                    .sorted(
                            Comparator.comparing(RunRecord::getSavedAt)
                                    .thenComparing(RunRecord::getRecordId))
                    .map(
                            r ->
                                    new SavedRun(
                                            r.getRecordId(),
                                            r.getMemberId(),
                                            r.getStartedAt(),
                                            r.getDistance(),
                                            r.isValid(),
                                            r.crewRunPostId(),
                                            r.getSavedAt()))
                    .toList();
        }

        @Override
        public List<ValidRunScore> validRunsAfter(long memberId, int ordinal) {
            List<RunRecord> valid =
                    of(memberId)
                            .filter(RunRecord::isValid)
                            .sorted(
                                    Comparator.comparing(RunRecord::getSavedAt)
                                            .thenComparing(RunRecord::getRecordId))
                            .toList();
            List<ValidRunScore> result = new java.util.ArrayList<>();
            for (int i = Math.max(0, ordinal); i < valid.size(); i++) {
                RunRecord r = valid.get(i);
                result.add(new ValidRunScore(i + 1, r.getScore().value(), r.getDistance()));
            }
            return result;
        }

        @Override
        public List<ValidRunStat> validRunStats(long memberId, int fromOrdinal, int toOrdinal) {
            List<RunRecord> valid =
                    of(memberId)
                            .filter(RunRecord::isValid)
                            .sorted(
                                    Comparator.comparing(RunRecord::getSavedAt)
                                            .thenComparing(RunRecord::getRecordId))
                            .toList();
            List<ValidRunStat> result = new java.util.ArrayList<>();
            for (int i = fromOrdinal - 1; i < Math.min(toOrdinal, valid.size()); i++) {
                RunRecord r = valid.get(i);
                HeartRateSummary hr = r.heartRateSummary();
                result.add(
                        new ValidRunStat(
                                i + 1,
                                r.getStartedAt(),
                                r.getDistance(),
                                r.getAveragePace(),
                                hr == null ? null : hr.average()));
            }
            return result;
        }

        @Override
        public Distance validDistanceSavedBefore(long memberId, java.time.LocalDateTime cutoff) {
            return Distance.ofMeters(
                    rows.values().stream()
                            .filter(r -> r.getMemberId() == memberId && r.isValid())
                            .filter(r -> r.getSavedAt() != null && r.getSavedAt().isBefore(cutoff))
                            .mapToLong(r -> r.getDistance().meters())
                            .sum());
        }

        @Override
        public List<SavedRun> tierPendingBetween(
                java.time.LocalDateTime from, java.time.LocalDateTime to) {
            return savedBetween(from, to).stream()
                    .filter(SavedRun::valid)
                    .filter(r -> !rows.get(r.recordId()).isTierApplied())
                    .toList();
        }

        @Override
        public Optional<ValidRunScore> validRunScoreOf(String recordId) {
            RunRecord target = rows.get(recordId);
            if (target == null || !target.isValid()) {
                return Optional.empty();
            }
            long before =
                    of(target.getMemberId())
                            .filter(RunRecord::isValid)
                            .filter(r -> savedBefore(r, target))
                            .count();
            return Optional.of(
                    new ValidRunScore(
                            (int) before + 1, target.getScore().value(), target.getDistance()));
        }

        private static boolean savedBefore(RunRecord r, RunRecord target) {
            int bySaved = r.getSavedAt().compareTo(target.getSavedAt());
            return bySaved < 0
                    || (bySaved == 0 && r.getRecordId().compareTo(target.getRecordId()) < 0);
        }

        @Override
        public boolean markTierApplied(String recordId, Optional<EvaluationView> evaluation) {
            RunRecord r = rows.get(recordId);
            if (r == null || !r.markTierApplied()) {
                return false;
            }
            evaluation.ifPresent(
                    e ->
                            r.markEvaluation(
                                    e.kind(),
                                    e.tier().name(),
                                    e.validRuns(),
                                    e.previousTier() == null ? null : e.previousTier().name()));
            return true;
        }

        /** 묘비(기록 삭제, v0.14 D-232): 기록 ID → 회원 ID. */
        private final Map<String, Long> tombstones = new LinkedHashMap<>();

        /** 다음 묘비 저장 한 번을 이 예외로 실패시킨다(TC-9353 롤백 시험). */
        public RuntimeException nextBuryFailure;

        @Override
        public void delete(RunRecord r) {
            rows.remove(r.getRecordId());
        }

        @Override
        public void bury(String recordId, long memberId, java.time.LocalDateTime at) {
            if (nextBuryFailure != null) {
                RuntimeException failure = nextBuryFailure;
                nextBuryFailure = null;
                throw failure;
            }
            tombstones.put(recordId, memberId);
        }

        @Override
        public boolean isBuried(String recordId) {
            return tombstones.containsKey(recordId);
        }

        /** 묘비 수(시험 확인용). */
        public int tombstoneCount() {
            return tombstones.size();
        }

        @Override
        public Optional<RunListRow> latestListRow(long memberId) {
            return newest(of(memberId))
                    .findFirst()
                    .map(
                            r ->
                                    new RunListRow(
                                            r.summary(),
                                            r.getNewRecords(),
                                            r.getRouteShape().orElse(null)));
        }

        /** listRows 호출 수(N+1 확인, TC-9428). */
        public int listRowsCalls;

        @Override
        public List<RunListRow> listRows(long memberId, DateRange range) {
            listRowsCalls++;
            return findByMemberBetween(memberId, range).stream()
                    .map(
                            r ->
                                    new RunListRow(
                                            r.summary(),
                                            r.getNewRecords(),
                                            r.getRouteShape().orElse(null)))
                    .toList();
        }

        @Override
        public List<RunRecord> findMissingShape(String afterRecordId, int limit) {
            return rows.values().stream()
                    .sorted(java.util.Comparator.comparing(RunRecord::getRecordId))
                    .filter(
                            r ->
                                    r.getRecordId().compareTo(afterRecordId) > 0
                                            && r.getRouteShape().isEmpty()
                                            && r.hasRoute()
                                            && r.routePoints().size() >= 2)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<EvaluationView> evaluationOf(String recordId) {
            return findById(recordId).flatMap(RunRecord::evaluation);
        }

        @Override
        public void noteRunimalType(String recordId, String runimalType) {
            findById(recordId).ifPresent(r -> r.markEvaluationRunimalType(runimalType));
        }

        @Override
        public List<String> rejudgeCandidates(RejudgeState state, int limit) {
            return rows.values().stream()
                    .filter(r -> r.rejudgeState() == state)
                    .sorted(
                            Comparator.comparing(RunRecord::getSavedAt)
                                    .thenComparing(RunRecord::getRecordId))
                    .limit(limit)
                    .map(RunRecord::getRecordId)
                    .toList();
        }

        @Override
        public Optional<RunRecord> findForUpdate(String recordId) {
            return findById(recordId);
        }

        @Override
        public boolean storeRejudgement(RunRecord record, RejudgeState from, RejudgeState to) {
            return moveRejudgeState(record.getRecordId(), from, to);
        }

        @Override
        public boolean moveRejudgeState(String recordId, RejudgeState from, RejudgeState to) {
            RunRecord r = rows.get(recordId);
            if (r == null || r.rejudgeState() != from) {
                return false;
            }
            r.markRejudgeState(to);
            return true;
        }

        public int size() {
            return rows.size();
        }

        private Stream<RunRecord> of(long memberId) {
            return rows.values().stream().filter(r -> r.getMemberId() == memberId);
        }

        private static Stream<RunRecord> newest(Stream<RunRecord> s) {
            return s.sorted(Comparator.comparing(RunRecord::getStartedAt).reversed());
        }

        private static boolean in(RunRecord r, DateRange range) {
            return range.contains(r.getStartedAt().toLocalDate());
        }

        private static RunSampleRow sample(RunRecord r) {
            return RunSampleRow.of(
                    r.getRecordId(),
                    r.getStartedAt(),
                    r.getDistance(),
                    r.getCardKind(),
                    r.summary().battleId());
        }
    }

    /** 인메모리 최고 기록 장부 저장소. */
    public static class Books implements PersonalRecordBookRepository {
        private final Map<Long, PersonalRecordBook> rows = new HashMap<>();
        private int lockCount;

        @Override
        public PersonalRecordBook findOrCreateForUpdate(long memberId) {
            lockCount++;
            return rows.computeIfAbsent(memberId, PersonalRecordBook::of);
        }

        @Override
        public PersonalRecordBook findOrCreate(long memberId) {
            return rows.getOrDefault(memberId, PersonalRecordBook.of(memberId));
        }

        @Override
        public void save(PersonalRecordBook book) {
            rows.put(book.memberId(), book);
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.remove(memberId);
        }

        public int lockCount() {
            return lockCount;
        }

        public boolean has(long memberId) {
            return rows.containsKey(memberId);
        }
    }

    /** 인메모리 파기 지시 저장소. */
    public static class Orders implements DisposalOrderRepository {
        private final Map<Long, DisposalOrder> rows = new LinkedHashMap<>();
        private long sequence;

        @Override
        public void save(DisposalOrder order) {
            if (order.id() == null) {
                setId(order, ++sequence);
            }
            rows.put(order.id(), order);
        }

        @Override
        public List<DisposalOrder> findScheduled() {
            return rows.values().stream()
                    .filter(o -> o.status() == DisposalStatus.SCHEDULED)
                    .toList();
        }

        @Override
        public List<DisposalOrder> findNotConfirmedByMember(long memberId) {
            return rows.values().stream()
                    .filter(o -> o.memberId() == memberId)
                    .filter(o -> o.deviceConfirmedAt() == null)
                    .toList();
        }

        @Override
        public Optional<DisposalOrder> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.values().removeIf(o -> o.memberId() == memberId);
        }

        public List<DisposalOrder> all() {
            return List.copyOf(rows.values());
        }

        private static void setId(DisposalOrder order, long id) {
            try {
                Field f = DisposalOrder.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(order, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
