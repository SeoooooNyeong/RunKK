package com.a207.runkk.common;

/** 성향 태그 분류 7개(srs.md 3.5.1). 1~4분류는 Runimal 4축과 같은 이름이다. */
public enum TagCategory {
    COMPANION(1, "동행"),
    DISTANCE(2, "거리"),
    COURSE(3, "코스"),
    HABIT(4, "습관"),
    RUN_TIME(5, "주로 뛰는 시간"),
    GOAL(6, "목표"),
    CREW_MOOD(7, "크루 분위기");

    private final int number;
    private final String label;

    TagCategory(int number, String label) {
        this.number = number;
        this.label = label;
    }

    /** 분류 번호 1~7. */
    public int number() {
        return number;
    }

    public String label() {
        return label;
    }
}
