package com.a207.runkk.common;

/**
 * 크루 태그 고정 목록 8개: 요일 묶음(평일, 주말) × 시간대(아침, 점심, 저녁, 새벽). 크루가 주로 함께 뛰는 때를 나타내고 크루 찾기 조건으로 쓴다(피그마
 * F-01b·F-04, S15P31A207-236에서 성향 태그 PersonalityTag를 대신했다). 운영 기준값이 아니라 고정 목록이다.
 */
public enum CrewTag {
    WEEKDAY_MORNING("평일 아침"),
    WEEKDAY_LUNCH("평일 점심"),
    WEEKDAY_EVENING("평일 저녁"),
    WEEKDAY_DAWN("평일 새벽"),
    WEEKEND_MORNING("주말 아침"),
    WEEKEND_LUNCH("주말 점심"),
    WEEKEND_EVENING("주말 저녁"),
    WEEKEND_DAWN("주말 새벽");

    private final String label;

    CrewTag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
