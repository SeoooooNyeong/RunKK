package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.RunningScore;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** analysis 시험용 값과 인메모리 저장소(설계 13.1 InMemory…Repository). */
public final class AnalysisFixtures {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private AnalysisFixtures() {}

    /** 유효 러닝 요약(점수는 VDOT). */
    public static RunSummary run(long memberId, LocalDateTime at, long meters, long seconds) {
        Distance distance = Distance.ofMeters(meters);
        Duration elapsed = Duration.ofSeconds(seconds);
        return new RunSummary(
                UUID.randomUUID().toString(),
                memberId,
                at,
                distance,
                elapsed,
                Pace.of(elapsed, distance),
                true,
                RunningScore.of(distance, elapsed).value(),
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null);
    }

    public static FitnessSummary summary(int toRun, long distanceChange, int paceChange) {
        return new FitnessSummary(
                Math.max(1, toRun - FitnessSummary.WINDOW + 1),
                toRun,
                new RunningScore(40),
                distanceChange,
                paceChange,
                java.time.DayOfWeek.SATURDAY,
                2,
                0);
    }

    /** 인메모리 목표 저장소. */
    public static class Goals implements RunningGoalRepository {
        final Map<Long, RunningGoal> rows = new HashMap<>();

        @Override
        public RunningGoal findOrCreate(long memberId) {
            return rows.getOrDefault(memberId, RunningGoal.create(memberId));
        }

        @Override
        public void save(RunningGoal goal) {
            rows.put(goal.getMemberId(), goal);
        }

        @Override
        public List<Long> membersWithPlanOn(java.time.LocalDate day) {
            return rows.values().stream()
                    .filter(g -> g.planOn(day).isPresent())
                    .map(RunningGoal::getMemberId)
                    .toList();
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.remove(memberId);
        }
    }

    /** 인메모리 리포트 저장소. */
    public static class Reports implements ReportRepository {
        final Map<Long, Report> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<Report> findByMember(long memberId) {
            return Optional.ofNullable(rows.get(memberId));
        }

        @Override
        public void save(Report report) {
            rows.put(report.getMemberId(), report);
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.remove(memberId);
        }
    }

    /** 인메모리 스냅숏 저장소(종류·기간마다 최근 하나). */
    public static class Snapshots implements RankingSnapshotRepository {
        final Map<String, RankingSnapshot> rows = new LinkedHashMap<>();

        @Override
        public void save(RankingSnapshot snapshot) {
            rows.put(snapshot.getKind() + ":" + snapshot.getPeriod(), snapshot);
        }

        @Override
        public Optional<RankingSnapshot> latest(RankingKind kind, AggregationPeriod period) {
            return Optional.ofNullable(rows.get(kind + ":" + period));
        }
    }

    /** 인메모리 카운터(원자적 증가). */
    public static class Counter implements QuotaCounter {
        final Map<String, AtomicLong> values = new ConcurrentHashMap<>();

        @Override
        public long incrementAndGet(String key, Duration ttl) {
            return values.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        }

        @Override
        public void decrement(String key) {
            values.computeIfAbsent(key, k -> new AtomicLong()).decrementAndGet();
        }

        long valueOf(String suffix) {
            return values.entrySet().stream()
                    .filter(e -> e.getKey().endsWith(suffix))
                    .mapToLong(e -> e.getValue().get())
                    .sum();
        }
    }
}
