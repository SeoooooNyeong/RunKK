package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 한 번 집계한 랭킹표(설계 6.1.4 RankingSnapshot, D-25, FR-070, FR-071). 매일 0시에 만들어 저장하고 조회는 이 표만 읽는다. */
@Entity
@Table(name = "ranking_snapshot")
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private RankingKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private AggregationPeriod period;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ranking_entry", joinColumns = @JoinColumn(name = "snapshot_id"))
    @OrderColumn(name = "entry_order")
    private List<EntryRow> entries = new ArrayList<>();

    protected RankingSnapshot() {}

    private RankingSnapshot(
            RankingKind kind,
            AggregationPeriod period,
            LocalDateTime computedAt,
            List<RankEntry> entries) {
        this.kind = kind;
        this.period = period;
        this.computedAt = computedAt;
        this.entries = new ArrayList<>(entries.stream().map(EntryRow::of).toList());
    }

    /**
     * 거리 내림차순으로 순위를 매긴다(6.2 A-19). 같은 거리는 같은 순위, 다음 순위는 건너뛴다(1, 2, 2, 4). 같은 거리 안 표시 순서는 subjectId
     * 오름차순이다.
     */
    public static RankingSnapshot build(
            RankingKind kind, AggregationPeriod period, LocalDateTime at, List<RankScore> scores) {
        List<RankScore> sorted =
                scores.stream()
                        .sorted(
                                Comparator.comparing(RankScore::distance)
                                        .reversed()
                                        .thenComparingLong(RankScore::subjectId))
                        .toList();
        List<RankEntry> ranked = new ArrayList<>();
        int rank = 0;
        Distance previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            RankScore score = sorted.get(i);
            if (!score.distance().equals(previous)) {
                rank = i + 1;
            }
            ranked.add(
                    new RankEntry(
                            rank, score.subjectId(), score.name(), score.tier(), score.distance()));
            previous = score.distance();
        }
        return new RankingSnapshot(kind, period, at, ranked);
    }

    /** 이미 순위를 매긴 줄로 되살린다(infra.analysis 캐시). */
    public static RankingSnapshot restore(
            RankingKind kind, AggregationPeriod period, LocalDateTime at, List<RankEntry> entries) {
        return new RankingSnapshot(kind, period, at, entries);
    }

    /** 본인(또는 본인 크루) 줄. 나만 보기면 비어 있다. */
    public Optional<RankEntry> entryOf(long subjectId) {
        return entries().stream().filter(e -> e.subjectId() == subjectId).findFirst();
    }

    /** 바로 위 순위까지 남은 거리. 1위거나 표에 없으면 비어 있다. */
    public Optional<Distance> gapToNext(long subjectId) {
        Optional<RankEntry> mine = entryOf(subjectId);
        if (mine.isEmpty() || mine.get().rank() == 1) {
            return Optional.empty();
        }
        Distance own = mine.get().distance();
        return entries().stream()
                .map(RankEntry::distance)
                .filter(d -> d.compareTo(own) > 0)
                .min(Comparator.naturalOrder())
                .map(above -> above.minus(own));
    }

    /** 상위 top줄과 subject의 줄·바로 위까지 거리(캐시가 비었을 때 DB 스냅숏에서 만든다). */
    public RankingPage page(int top, Optional<Long> subjectId) {
        List<RankEntry> all = entries();
        return new RankingPage(
                computedAt,
                all.subList(0, Math.min(top, all.size())),
                subjectId.flatMap(this::entryOf).orElse(null),
                subjectId.flatMap(this::gapToNext).orElse(null));
    }

    /** 줄마다 바로 위 순위까지 거리(1위는 없음). 순위순 한 번 훑어 구한다(캐시의 회원별 줄). */
    public Map<Long, Distance> gapsToNext() {
        Map<Long, Distance> gaps = new HashMap<>();
        Distance above = null;
        Distance group = null;
        for (RankEntry e : entries()) {
            if (group != null && !e.distance().equals(group)) {
                above = group;
            }
            group = e.distance();
            if (above != null) {
                gaps.put(e.subjectId(), above.minus(e.distance()));
            }
        }
        return gaps;
    }

    public RankingKind getKind() {
        return kind;
    }

    public AggregationPeriod getPeriod() {
        return period;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    /** 순위순 줄(읽기 전용). */
    public List<RankEntry> entries() {
        return entries.stream().map(EntryRow::toEntry).toList();
    }

    /**
     * 저장 줄.
     *
     * @param rankNo 순위
     * @param subjectId 회원 또는 크루 ID
     * @param displayName 표시명
     * @param tier 티어(크루는 null)
     * @param distanceMeters 거리(미터)
     */
    @Embeddable
    record EntryRow(
            @Column(name = "rank_no", nullable = false) int rankNo,
            @Column(name = "subject_id", nullable = false) long subjectId,
            @Column(name = "display_name", nullable = false, length = 40) String displayName,
            @Enumerated(EnumType.STRING) @Column(name = "tier", length = 10) Tier tier,
            @Column(name = "distance_meters", nullable = false) long distanceMeters) {

        static EntryRow of(RankEntry e) {
            return new EntryRow(
                    e.rank(),
                    e.subjectId(),
                    e.displayName().value(),
                    e.tier(),
                    e.distance().meters());
        }

        RankEntry toEntry() {
            return new RankEntry(
                    rankNo,
                    subjectId,
                    DisplayName.of(displayName),
                    tier,
                    Distance.ofMeters(distanceMeters));
        }
    }
}
