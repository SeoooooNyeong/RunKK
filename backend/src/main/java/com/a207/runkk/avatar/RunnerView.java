package com.a207.runkk.avatar;

import com.a207.runkk.common.Distance;

/**
 * 내 러너 화면(피그마 H-2)과 W9 렌더러 입력(W8, S15P31A207-299·300, contract-W8.md 2.1·6절). web.avatar
 * RunnerResponse로 바꾼다.
 *
 * @param bodyShape 지금 체형(4단계, FR-163)
 * @param skinTone 지금 피부톤(시간대, FR-164)
 * @param typicalStartHour 피부톤 근거 대표 시각(시), 지금 톤 시간대의 러닝이 없으면 null
 * @param recentRunCount 오늘 포함 최근 7일 유효 러닝 수
 * @param recentDistance 같은 7일 유효 거리 합
 * @param restDaysBeforeDecline 체형규칙 쉼 일수
 */
public record RunnerView(
        BodyShape bodyShape,
        SkinTone skinTone,
        Integer typicalStartHour,
        int recentRunCount,
        Distance recentDistance,
        int restDaysBeforeDecline) {

    /** 화면·렌더러용 3단계 체형(0 기본, 1 변화 1, 2 변화 2). */
    public int bodyVariant() {
        return bodyShape.variant();
    }

    /** W9 피부톤 입력 러닝량 0~100. */
    public int runAmount() {
        return AppearanceCalculator.runAmount(recentDistance);
    }
}
