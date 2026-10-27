package com.a207.runkk.crew;

import com.a207.runkk.report.target.CrewPostReportAdapter;

/** ArchUnit 음성 시험 전용(규칙 8 위반, TC-6206 ④). 도메인 패키지는 조립 패키지 report.target을 import하지 않는다(D-135). */
public class ArchFixtureCrewUsesReportTarget {
    public boolean has(CrewPostReportAdapter adapter) {
        return adapter != null;
    }
}
