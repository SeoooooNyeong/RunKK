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
        public List<RunSummary> summariesBetween(long memberId, DateRange range) {
            return findByMemberBetween(memberId, range).stream().map(RunRecord::summary).toList();
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
            List<RunRecord> all = findByMember(memberId);
            return new TotalStats(
                    all.stream().map(RunRecord::getDistance).reduce(Distance.ZERO, Distance::plus),
                    all.stream().map(RunRecord::getElapsed).reduce(Duration.ZERO, Duration::plus),
                    all.size(),
                    all.stream()
                            .filter(RunRecord::isValid)
                            .map(RunRecord::getDistance)
                            .reduce(Distance.ZERO, Distance::plus));
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
            return new RunSampleRow(r.getRecordId(), r.getStartedAt(), r.getDistance());
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
