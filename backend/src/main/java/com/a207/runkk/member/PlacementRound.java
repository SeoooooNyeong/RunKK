package com.a207.runkk.member;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 센 배치고사 판 하나의 기록 값(05-21 2.1, 표 placement_round). 셀 때 대결·러닝 기록에서 값을 복사해 두어 대결 행의 보관·탈퇴 처리와 수명을 엮지
 * 않는다(D-475). 판정 정정만 승패를 바꾼다(withVerdict).
 */
@Embeddable
public class PlacementRound {

    @Column(name = "round_no", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int round;

    @Column(name = "battle_id", nullable = false)
    private long battleId;

    @Column(name = "score", nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(name = "battle_distance", nullable = false, length = 10)
    private StandardDistance battleDistance;

    @Column(name = "run_distance_meters", nullable = false)
    private long runDistanceMeters;

    @Column(name = "run_elapsed_millis", nullable = false)
    private long runElapsedMillis;

    @Column(name = "average_pace_seconds")
    private Integer averagePaceSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 5)
    private PlacementVerdict verdict;

    @Column(name = "valid_run_ordinal", nullable = false)
    private int validRunOrdinal;

    @Column(name = "counted_at", nullable = false)
    private LocalDateTime countedAt;

    protected PlacementRound() {}

    static PlacementRound of(int round, PlacementRoundInput in) {
        PlacementRound r = new PlacementRound();
        r.round = round;
        r.battleId = in.battleId();
        r.score = in.score();
        r.battleDistance = in.battleDistance();
        r.runDistanceMeters = in.runDistance().meters();
        r.runElapsedMillis = in.runElapsed().toMillis();
        r.averagePaceSeconds = in.averagePace() == null ? null : in.averagePace().secondsPerKm();
        r.verdict = in.verdict();
        r.validRunOrdinal = in.validRunOrdinal();
        r.countedAt = in.countedAt();
        return r;
    }

    /** 승패만 바꾼 사본(판정 정정). */
    PlacementRound withVerdict(PlacementVerdict corrected) {
        PlacementRound copy = new PlacementRound();
        copy.round = round;
        copy.battleId = battleId;
        copy.score = score;
        copy.battleDistance = battleDistance;
        copy.runDistanceMeters = runDistanceMeters;
        copy.runElapsedMillis = runElapsedMillis;
        copy.averagePaceSeconds = averagePaceSeconds;
        copy.verdict = corrected;
        copy.validRunOrdinal = validRunOrdinal;
        copy.countedAt = countedAt;
        return copy;
    }

    /** 판 번호 1~3(셀 때 정한 번호, D-483). */
    public int round() {
        return round;
    }

    public long battleId() {
        return battleId;
    }

    public double score() {
        return score;
    }

    /** 고른 대결 거리. */
    public StandardDistance battleDistance() {
        return battleDistance;
    }

    /** 그 판 러닝 기록 거리. */
    public Distance runDistance() {
        return Distance.ofMeters(runDistanceMeters);
    }

    /** 그 판 러닝 기록 운동 시간. */
    public Duration runElapsed() {
        return Duration.ofMillis(runElapsedMillis);
    }

    /** 평균 페이스, 기록 요약에 없으면 비어 있다. */
    public Optional<Pace> averagePace() {
        return Optional.ofNullable(averagePaceSeconds).map(Pace::new);
    }

    public PlacementVerdict verdict() {
        return verdict;
    }

    /** 그 판 기록의 유효 러닝 순번(D-482). */
    public int validRunOrdinal() {
        return validRunOrdinal;
    }

    public LocalDateTime countedAt() {
        return countedAt;
    }
}
