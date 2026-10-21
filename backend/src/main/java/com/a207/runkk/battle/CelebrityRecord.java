package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 운영자가 등록한 유명인 공개 기록 하나(설계 6.1.6, FR-170~FR-172). 표시 티어는 없다(UNRANKED). 구간 기록이 없으면 평균 페이스로
 * 추정한다(A-006).
 */
@Entity
@Table(name = "celebrity_record")
public class CelebrityRecord implements OpponentRecord {

    /** 익명 닉네임 최대 길이(대결 표시명). */
    static final int NAME_MAX = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_name", nullable = false, length = NAME_MAX)
    private String anonymousName;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "finish_millis", nullable = false)
    private long finishMillis;

    @Column(name = "splits", columnDefinition = "text")
    private String splits;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    protected CelebrityRecord() {}

    private CelebrityRecord(CelebrityInput in, OpponentTrack track, LocalDateTime now) {
        this.anonymousName = in.anonymousName().strip();
        this.distanceMeters = in.distance().meters();
        this.finishMillis = in.finishTime().toMillis();
        this.splits = track.isEmpty() ? null : track.encode();
        this.sourceName = in.sourceName().strip();
        this.sourceUrl = in.sourceUrl();
        this.registeredAt = now;
    }

    /**
     * 등록(srs.md 6.3 7.1). 출처명 빔 → SOURCE_REQUIRED, 익명 닉네임 빔 → ANONYMOUS_NAME_REQUIRED, 거리·시간 ≤ 0 →
     * VALUE_INVALID, 구간이 줄어듦 → SPLITS_INVALID.
     */
    public static CelebrityRecord register(CelebrityInput in, LocalDateTime now) {
        if (isBlank(in.sourceName())) {
            throw new BusinessRuleException(ReasonCode.SOURCE_REQUIRED);
        }
        if (isBlank(in.anonymousName()) || in.anonymousName().strip().length() > NAME_MAX) {
            throw new BusinessRuleException(ReasonCode.ANONYMOUS_NAME_REQUIRED);
        }
        if (in.distance() == null || in.distance().meters() <= 0 || !isPositive(in.finishTime())) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        if (!OpponentTrack.isOrdered(in.splits())) {
            throw new BusinessRuleException(ReasonCode.SPLITS_INVALID);
        }
        return new CelebrityRecord(in, OpponentTrack.of(in.splits()), now);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPositive(Duration d) {
        return d != null && !d.isNegative() && !d.isZero();
    }

    /** 구간 기록이 없으면 OpponentTrack.uniform(distance, finishTime). */
    @Override
    public OpponentTrack track(Distance d) {
        return storedSplits()
                .orElseGet(() -> OpponentTrack.uniform(getDistance(), getFinishTime()))
                .cut(d);
    }

    private Optional<OpponentTrack> storedSplits() {
        return Optional.ofNullable(splits).map(OpponentTrack::decode);
    }

    /** 구간 기록이 없어 평균 페이스로 추정한 표인가(화면 "평균 페이스로 추정", A-006). */
    public boolean isEstimated() {
        return splits == null;
    }

    @Override
    public DisplayName displayName() {
        return DisplayName.of(anonymousName);
    }

    @Override
    public Tier displayTier() {
        return Tier.UNRANKED;
    }

    @Override
    public OpponentKind kind() {
        return OpponentKind.CELEBRITY;
    }

    @Override
    public String recordId() {
        return String.valueOf(id);
    }

    /** 기록·평균 페이스만. 가장 빠른 1km는 구간 기록이 있을 때만(FR-093). */
    @Override
    public ComparisonValues comparisonValues(Distance d) {
        Pace average = Pace.of(getFinishTime(), getDistance());
        Pace fastest =
                storedSplits()
                        .flatMap(OpponentTrack::fastestKm)
                        .map(split -> Pace.of(split, Distance.ofMeters(Distance.METERS_PER_KM)))
                        .orElse(null);
        return new ComparisonValues(getFinishTime(), average, fastest, null, null);
    }

    public Long getId() {
        return id;
    }

    public String getAnonymousName() {
        return anonymousName;
    }

    public Distance getDistance() {
        return Distance.ofMeters(distanceMeters);
    }

    public Duration getFinishTime() {
        return Duration.ofMillis(finishMillis);
    }

    public Source getSource() {
        return new Source(sourceName, sourceUrl);
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
