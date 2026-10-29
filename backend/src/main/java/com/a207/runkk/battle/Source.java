package com.a207.runkk.battle;

/**
 * 유명인 기록 출처(설계 6.1.6, FR-100).
 *
 * @param name 출처명, 비지 않음
 * @param url 출처 주소, 없으면 null
 */
public record Source(String name, String url) {

    /** 출처 표기 "출처: {name}"(FR-100, v0.14 D-239). */
    private static final String LABEL_PREFIX = "출처: ";

    public Source {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("출처명이 비었다");
        }
    }

    /** "출처: {name}"(FR-100, v0.14 D-239). */
    public String label() {
        return LABEL_PREFIX + name;
    }
}
