package com.a207.runkk.common;

/**
 * 표시명 = 닉네임 | 익명닉네임 | 가상닉네임 | 탈퇴한사용자(srs.md 6.2). 표시명 규칙을 한곳에 둔다(설계 6.1.1).
 *
 * @param value 비지 않은 문자열
 */
public record DisplayName(String value) {

    public static final DisplayName WITHDRAWN = new DisplayName("탈퇴한 사용자");

    public DisplayName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("표시명이 비었다");
        }
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }

    /** null이면 WITHDRAWN. */
    public static DisplayName ofNullable(String value) {
        return value == null ? WITHDRAWN : new DisplayName(value);
    }
}
