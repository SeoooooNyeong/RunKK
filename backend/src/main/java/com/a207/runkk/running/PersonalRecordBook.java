package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 회원의 거리별 최고 기록(설계 6.1.3, A-05). 기준 거리 5개(1·3·5·10km·하프)와 최장 거리. 저장 때 계산해 두므로 경로를 파기해도 남는다. 같은 회원의
 * 업로드를 이 행의 잠금으로 직렬화한다(5.3, D-37).
 */
@Entity
@Table(name = "personal_record_book")
public class PersonalRecordBook {

    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SECONDS_PER_MINUTE = 60;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "personal_best", joinColumns = @JoinColumn(name = "member_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "distance_key", length = 10)
    private Map<StandardDistance, BestEntry> bests = new EnumMap<>(StandardDistance.class);

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "recordId", column = @Column(name = "longest_record_id")),
        @AttributeOverride(
                name = "elapsedMillis",
                column = @Column(name = "longest_elapsed_millis")),
        @AttributeOverride(
                name = "distanceMeters",
                column = @Column(name = "longest_distance_meters")),
        @AttributeOverride(name = "achievedOn", column = @Column(name = "longest_achieved_on")),
        @AttributeOverride(name = "hasUnstable", column = @Column(name = "longest_has_unstable"))
    })
    private BestEntry longest;

    protected PersonalRecordBook() {}

    private PersonalRecordBook(long memberId) {
        this.memberId = memberId;
    }

    /** 아직 기록이 없는 회원의 빈 장부. */
    public static PersonalRecordBook of(long memberId) {
        return new PersonalRecordBook(memberId);
    }

    /**
     * 유효 러닝을 반영한다. 기준 거리마다 도달 시간이 기존보다 짧으면 갱신하고 신기록 표시를, 거리가 기존 최장보다 길면 최장을 갱신한다. 첫 기록에는 신기록 표시를
     * 붙이지 않는다(FR-063, FR-076, FR-186).
     */
    public List<NewRecordMark> apply(RunRecord r) {
        List<NewRecordMark> marks = new ArrayList<>();
        if (!r.isValid()) {
            return marks;
        }
        for (StandardDistance d : StandardDistance.values()) {
            if (d.isRecordBase()) {
                r.timeToReach(d.distance())
                        .flatMap(reached -> improveBest(r, d, reached))
                        .ifPresent(marks::add);
            }
        }
        improveLongest(r).ifPresent(marks::add);
        return marks;
    }

    /** 기준 거리 d의 최고 기록을 갱신한다. 갱신했고 직전 기록이 있었으면 신기록 표시(sw-test 복잡도 분리). */
    private Optional<NewRecordMark> improveBest(RunRecord r, StandardDistance d, Duration reached) {
        BestEntry old = bests.get(d);
        Duration oldTime = old == null ? null : old.time().orElse(null);
        if (oldTime != null && reached.compareTo(oldTime) >= 0) {
            return Optional.empty();
        }
        bests.put(
                d,
                BestEntry.ofTime(
                        r.getRecordId(),
                        reached,
                        r.getStartedAt().toLocalDate(),
                        r.track().hasUnstableWithin(Distance.ZERO, d.distance())));
        return Optional.ofNullable(oldTime)
                .map(t -> new NewRecordMark(NewRecordMark.Kind.BASE_DISTANCE, d, format(t)));
    }

    /** 최장 거리를 갱신한다. 갱신했고 직전 최장이 있었으면 신기록 표시. */
    private Optional<NewRecordMark> improveLongest(RunRecord r) {
        Distance previousLongest = longest == null ? null : longest.distance().orElse(null);
        if (previousLongest != null && r.getDistance().compareTo(previousLongest) <= 0) {
            return Optional.empty();
        }
        this.longest =
                BestEntry.ofDistance(
                        r.getRecordId(),
                        r.getDistance(),
                        r.getStartedAt().toLocalDate(),
                        !r.track().unstableSegments().isEmpty());
        return Optional.ofNullable(previousLongest)
                .map(
                        p ->
                                new NewRecordMark(
                                        NewRecordMark.Kind.LONGEST,
                                        null,
                                        String.format(Locale.ROOT, "직전 %.2f km", p.km())));
    }

    /** 거리별 최고. d는 기준 거리여야 한다. */
    public Optional<BestEntry> best(StandardDistance d) {
        if (!d.isRecordBase()) {
            throw new IllegalArgumentException("기록 기준 거리가 아니다: " + d);
        }
        return Optional.ofNullable(bests.get(d));
    }

    public Optional<BestEntry> longest() {
        return Optional.ofNullable(longest);
    }

    long memberId() {
        return memberId;
    }

    private static String format(Duration t) {
        long seconds = t.toSeconds();
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long rest = seconds % SECONDS_PER_MINUTE;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
