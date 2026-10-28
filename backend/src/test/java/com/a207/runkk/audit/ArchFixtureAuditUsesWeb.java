package com.a207.runkk.audit;

import com.a207.runkk.web.error.ArchFixtureWebValue;

/** ArchUnit 음성 시험 전용(규칙 1·3 위반). 도메인 공통 audit이 표현 계층 web.error를 import한다. */
public class ArchFixtureAuditUsesWeb {
    public String codeOf(ArchFixtureWebValue value) {
        return value.code();
    }
}
