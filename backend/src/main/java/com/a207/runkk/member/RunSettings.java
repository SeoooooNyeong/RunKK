package com.a207.runkk.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 자유런 기본 설정(설계 6.1.2, FR-048).
 *
 * @param target 목표 거리
 * @param voice 음성 안내
 * @param vibration 진동 안내
 * @param followCoachPlan 코치 계획 따르기
 */
@Embeddable
public record RunSettings(
        @Enumerated(EnumType.STRING) @Column(name = "run_target", length = 10) FreeRunTarget target,
        @Column(name = "run_voice") boolean voice,
        @Column(name = "run_vibration") boolean vibration,
        @Column(name = "run_follow_coach_plan") boolean followCoachPlan) {

    public RunSettings {
        if (target == null) {
            throw new IllegalArgumentException("목표 거리가 비었다");
        }
    }

    /** 목표 자유, 음성 켬, 진동 켬, 코치 계획 따르기 꺼짐(srs.md 6.3 1.2). */
    public static RunSettings defaults() {
        return new RunSettings(FreeRunTarget.FREE, true, true, false);
    }
}
