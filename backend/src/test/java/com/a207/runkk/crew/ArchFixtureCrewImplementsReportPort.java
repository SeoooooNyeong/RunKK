package com.a207.runkk.crew;

import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportedContent;
import com.a207.runkk.report.ReportedContentPort;
import java.util.Optional;

/** ArchUnit 음성 시험 전용(규칙 1 위반, TC-6206 ①·TC-6831). crew는 report 포트를 구현하지 않는다(N6 삭제, D-135). */
public class ArchFixtureCrewImplementsReportPort implements ReportedContentPort {
    @Override
    public boolean supports(ReportTargetKind kind) {
        return false;
    }

    @Override
    public Optional<ReportedContent> find(ReportTargetKind kind, long targetId) {
        return Optional.empty();
    }

    @Override
    public void hide(ReportTargetKind kind, long targetId) {}

    @Override
    public void unhide(ReportTargetKind kind, long targetId) {}
}
