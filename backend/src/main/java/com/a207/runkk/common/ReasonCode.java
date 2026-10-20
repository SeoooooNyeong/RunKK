package com.a207.runkk.common;

/**
 * 업무 규칙 거부 사유 코드(설계 6.1, 13.1 "BusinessRuleException(사유 코드 enum)"). 상수는 srs.md 6.3의 거부 사유 문구와 1:1로
 * 대응하고 ApiExceptionHandler가 문구로 바꾼다. 상수는 그 사유를 처음 쓰는 패키지를 구현할 때 더한다.
 */
public enum ReasonCode {
    /** 성향 태그 개수·분류 규칙 위반(srs.md 6.3 1.2, Q-82). */
    TAG_INVALID("태그 오류"),
    /** 운영 기준값 형식·범위 위반(srs.md 6.3 7.2, FR-174). */
    VALUE_INVALID("값 오류");

    private final String message;

    ReasonCode(String message) {
        this.message = message;
    }

    /** 화면 문구. */
    public String message() {
        return message;
    }
}
