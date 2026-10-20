package com.a207.runkk.member;

import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 배틀런 기본 설정(설계 6.1.2, FR-109).
 *
 * @param defaultDistance 기본 대결 거리, 랜덤 대결 거리만
 * @param voice 음성 안내
 * @param vibration 진동 안내
 * @param gapAlertInterval 중간 알림 간격
 */
@Embeddable
public record BattleSettings(
        @Enumerated(EnumType.STRING) @Column(name = "battle_default_distance", length = 10)
                StandardDistance defaultDistance,
        @Column(name = "battle_voice") boolean voice,
        @Column(name = "battle_vibration") boolean vibration,
        @Enumerated(EnumType.STRING) @Column(name = "battle_gap_alert_interval", length = 10)
                GapAlertInterval gapAlertInterval) {

    public BattleSettings {
        if (defaultDistance == null || !defaultDistance.isRandomBattle()) {
            throw new IllegalArgumentException("기본 대결 거리는 1·3·5·10km: " + defaultDistance);
        }
        if (gapAlertInterval == null) {
            throw new IllegalArgumentException("중간 알림 간격이 비었다");
        }
    }

    /** 5km, 음성 켬, 진동 켬, 1km(srs.md 6.3 1.2). */
    public static BattleSettings defaults() {
        return new BattleSettings(StandardDistance.KM5, true, true, GapAlertInterval.KM1);
    }

    public BattleSettings withDefaultDistance(StandardDistance d) {
        return new BattleSettings(d, voice, vibration, gapAlertInterval);
    }
}
