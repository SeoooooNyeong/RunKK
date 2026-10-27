package com.a207.runkk.report;

import com.a207.runkk.crew.PostKind;

/** ArchUnit 음성 시험 전용(규칙 1 위반, TC-6831). report는 crew를 모른다(대응표는 report.target, H-31). */
public class ArchFixtureReportUsesCrew {
    public String name(PostKind kind) {
        return kind.name();
    }
}
