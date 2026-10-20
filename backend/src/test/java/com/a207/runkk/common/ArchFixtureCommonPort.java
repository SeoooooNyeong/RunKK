package com.a207.runkk.common;

/** ArchUnit 음성 시험 전용(규칙 6 위반). common에 포트 인터페이스를 둔다. */
public interface ArchFixtureCommonPort {
    void send(String message);
}
