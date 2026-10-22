package com.a207.runkk.battle;

/**
 * 유명인 기록 출처(설계 6.1.6, FR-100).
 *
 * @param name 출처명, 비지 않음
 * @param url 출처 주소, 없으면 null
 */
public record Source(String name, String url) {

    private static final String LABEL_PREFIX = "공개 기록 기준 · ";

    public Source {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("출처명이 비었다");
        }
    }

    /** "공개 기록 기준 · {name}"(FR-100). */
    public String label() {
        return LABEL_PREFIX + name;
    }
}
