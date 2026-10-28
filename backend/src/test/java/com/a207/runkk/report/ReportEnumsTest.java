package com.a207.runkk.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 report enum(F.5.1, 06-05, D-138). TC-6203. */
@DisplayName("v0.11 report enum")
class ReportEnumsTest {

    @Test
    @DisplayName("신고 대상 종류 6, 사유 5, 처리 상태 4")
    void reportEnums() {
        assertThat(ReportTargetKind.values())
                .containsExactly(
                        ReportTargetKind.CHEER,
                        ReportTargetKind.SCHEDULE,
                        ReportTargetKind.NOTICE,
                        ReportTargetKind.POLL,
                        ReportTargetKind.BOARD,
                        ReportTargetKind.CHAT_MESSAGE);
        assertThat(ReportReason.values())
                .containsExactly(
                        ReportReason.ABUSE,
                        ReportReason.SEXUAL,
                        ReportReason.SPAM,
                        ReportReason.PRIVACY,
                        ReportReason.OTHER);
        assertThat(ReportStatus.values())
                .containsExactly(
                        ReportStatus.RECEIVED,
                        ReportStatus.AUTO_HIDDEN,
                        ReportStatus.HIDDEN,
                        ReportStatus.DISMISSED);
    }

    @Test
    @DisplayName("ReportDecision은 지웠다(D-138: hide·dismiss 연산 둘)")
    void reportDecision_doesNotExist() {
        assertThatThrownBy(() -> Class.forName("com.a207.runkk.report.ReportDecision"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
