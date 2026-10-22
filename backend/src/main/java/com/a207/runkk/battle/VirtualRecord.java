package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

/**
 * 고스트 후보가 없을 때의 가상 기록(설계 6.1.6, FR-104, D16). 균등 페이스이고 생성은 VirtualRecordFactory만 한다. 랭킹·대시보드·크루 집계는
 * 이 저장소를 읽지 않는다(FR-105).
 */
@Entity
@Table(name = "virtual_record")
public class VirtualRecord implements OpponentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 10)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "distance", nullable = false, length = 10)
    private StandardDistance distance;

    @Column(name = "nickname", nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "finish_millis", nullable = false)
    private long finishMillis;

    protected VirtualRecord() {}

    VirtualRecord(Tier tier, StandardDistance distance, String nickname, Duration finishTime) {
        if (!tier.isRanked() || finishTime.isNegative() || finishTime.isZero()) {
            throw new IllegalArgumentException("가상 기록은 랭크 티어·양의 완주 시간이다");
        }
        this.tier = tier;
        this.distance = distance;
        this.nickname = DisplayName.of(nickname).value();
        this.finishMillis = finishTime.toMillis();
    }

    @Override
    public OpponentTrack track(Distance battleDistance) {
        return OpponentTrack.uniform(distance.distance(), getFinishTime()).cut(battleDistance);
    }

    @Override
    public DisplayName displayName() {
        return DisplayName.of(nickname);
    }

    @Override
    public Tier displayTier() {
        return tier;
    }

    @Override
    public OpponentKind kind() {
        return OpponentKind.VIRTUAL;
    }

    @Override
    public String recordId() {
        return String.valueOf(id);
    }

    /** 균등 페이스라 가장 빠른 1km도 평균 페이스와 같다. */
    @Override
    public ComparisonValues comparisonValues(Distance battleDistance) {
        Pace pace = Pace.of(getFinishTime(), distance.distance());
        return new ComparisonValues(getFinishTime(), pace, pace, null, null);
    }

    public Long getId() {
        return id;
    }

    public Tier getTier() {
        return tier;
    }

    public StandardDistance getDistance() {
        return distance;
    }

    public String getNickname() {
        return nickname;
    }

    public Duration getFinishTime() {
        return Duration.ofMillis(finishMillis);
    }
}
