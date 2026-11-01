package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
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

    /** 캐릭터 그림 키 최대 길이(V32). */
    static final int FIGURE_KEY_MAX = 20;

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

    /** 실명 등록 동의 일시(v0.14 D-240). 근거와 둘 다 있거나 둘 다 없다(V30 CHECK). */
    @Column(name = "real_name_consented_at")
    private LocalDateTime realNameConsentedAt;

    /** 실명 등록 동의 근거. */
    @Column(name = "real_name_consent_basis", length = 200)
    private String realNameConsentBasis;

    /** 앱 전용 캐릭터 그림 키(2026-10-31 결정). null이면 앱이 공통 셀럽 그림을 쓴다. */
    @Column(name = "figure_key", length = FIGURE_KEY_MAX)
    private String figureKey;

    protected CelebrityRecord() {}

    private CelebrityRecord(CelebrityInput in, OpponentTrack track, LocalDateTime now) {
        this.anonymousName = in.anonymousName().strip();
        this.distanceMeters = in.distance().meters();
        this.finishMillis = in.finishTime().toMillis();
        this.splits = track.isEmpty() ? null : track.encode();
        this.sourceName = in.sourceName().strip();
        this.sourceUrl = in.sourceUrl();
        this.registeredAt = now;
        if (in.realNameConsent() != null) {
            this.realNameConsentedAt = in.realNameConsent().consentedAt();
            this.realNameConsentBasis = in.realNameConsent().basis().strip();
        }
        this.figureKey = isBlank(in.figureKey()) ? null : in.figureKey().strip();
    }

    /**
     * 등록(srs.md 6.3 7.1). 출처명 빔 → SOURCE_REQUIRED, 익명 닉네임 빔 → ANONYMOUS_NAME_REQUIRED, 거리·시간 ≤ 0 →
     * VALUE_INVALID, 거리가 5·10km·하프·풀이 아님 → BATTLE_DISTANCE_INVALID(배틀런 QA 결정), 구간이 줄어듦 →
     * SPLITS_INVALID. 캐릭터 그림 키가 20자를 넘으면 VALUE_INVALID, 비어 있으면 없는 것으로 본다.
     */
    public static CelebrityRecord register(CelebrityInput in, LocalDateTime now) {
        // 실명 등록은 코치 동의 일시·근거가 함께 있어야 한다(FR-170, v0.14 D-240). 이름 칸은 표시명 그대로 쓴다
        if (in.realNameConsent() != null && !in.realNameConsent().isComplete()) {
            throw new BusinessRuleException(ReasonCode.REAL_NAME_CONSENT_REQUIRED);
        }
        if (isBlank(in.sourceName())) {
            throw new BusinessRuleException(ReasonCode.SOURCE_REQUIRED);
        }
        if (isBlank(in.anonymousName()) || in.anonymousName().strip().length() > NAME_MAX) {
            throw new BusinessRuleException(ReasonCode.ANONYMOUS_NAME_REQUIRED);
        }
        if (in.distance() == null
                || in.distance().meters() <= 0
                || !isPositive(in.finishTime())
                || (!isBlank(in.figureKey()) && in.figureKey().strip().length() > FIGURE_KEY_MAX)) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        if (!isCelebrityDistance(in.distance())) {
            throw new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID);
        }
        if (!OpponentTrack.isOrdered(in.splits())) {
            throw new BusinessRuleException(ReasonCode.SPLITS_INVALID);
        }
        return new CelebrityRecord(in, OpponentTrack.of(in.splits()), now);
    }

    private static boolean isCelebrityDistance(Distance d) {
        return Arrays.stream(StandardDistance.values())
                .anyMatch(s -> s.isCelebrity() && s.distance().equals(d));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    /** 구간 기록이 없으면 OpponentTrack.uniform(distance, finishTime). */
    @Override
    public OpponentTrack track(Distance d) {
        return storedSplits()
                .orElseGet(() -> OpponentTrack.uniform(getDistance(), getFinishTime()))
                .cut(d);
    }

    /**
     * 상대 표가 기록 거리에 닿는가(track(distance)와 같은 판단을 표를 만들지 않고 한다, W31). 구간 기록이 없으면 균등 페이스 표라 늘 닿고, 있으면
     * 저장된 구간(몇 점)만 본다.
     */
    public boolean hasReachableTrack() {
        return storedSplits().map(t -> t.arrivalTime(getDistance()).isPresent()).orElse(true);
    }

    private Optional<OpponentTrack> storedSplits() {
        return Optional.ofNullable(splits).map(OpponentTrack::decode);
    }

    @Override
    public Optional<String> figureKey() {
        return Optional.ofNullable(figureKey);
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

    /** 실명 동의를 받아 실명으로 등록한 기록인가(v0.14 D-240). */
    public boolean isRealName() {
        return realNameConsentedAt != null;
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
