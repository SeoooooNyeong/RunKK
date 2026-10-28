package com.a207.runkk.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 설계 6.1.12a ReportCommand(D-127, D-143, D-149). TC-6133. */
@DisplayName("report ReportCommand")
class ReportCommandTest {

    private static ReportCommand other(String detail) {
        return new ReportCommand(ReportTargetKind.CHEER, 100, ReportReason.OTHER, detail);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("TC-6133: 기타 사유에 내용이 없거나 공백뿐이면 IllegalArgumentException(→ 400)")
    void other_requiresDetail(String detail) {
        assertThatThrownBy(() -> other(detail)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-6133: 기타 200자·이모지 200개는 생성, 201자·이모지 201개는 거부(코드 포인트, 앞뒤 공백 제거 뒤)")
    void other_detailLengthInCodePoints() {
        assertThat(other("가".repeat(200)).detail()).hasSize(200);
        assertThat(other("  " + "가".repeat(200) + "  ").detail()).isEqualTo("가".repeat(200));
        assertThatThrownBy(() -> other("가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(other("🏃".repeat(200)).detail()).isEqualTo("🏃".repeat(200));
        assertThatThrownBy(() -> other("🏃".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-6133: 다른 사유는 내용 없어도 생성(공백뿐이면 null), 201자는 거부")
    void otherReasons_detailOptional() {
        assertThat(new ReportCommand(ReportTargetKind.BOARD, 1, ReportReason.ABUSE, null).detail())
                .isNull();
        assertThat(new ReportCommand(ReportTargetKind.BOARD, 1, ReportReason.SPAM, "  ").detail())
                .isNull();
        assertThat(new ReportCommand(ReportTargetKind.BOARD, 1, ReportReason.SPAM, " 광고 ").detail())
                .isEqualTo("광고");
        assertThatThrownBy(
                        () ->
                                new ReportCommand(
                                        ReportTargetKind.BOARD,
                                        1,
                                        ReportReason.ABUSE,
                                        "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대상 종류·사유가 비거나 대상 ID가 양수가 아니면 IllegalArgumentException")
    void requiredFields() {
        assertThatThrownBy(() -> new ReportCommand(null, 1, ReportReason.ABUSE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportCommand(ReportTargetKind.CHEER, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new ReportCommand(ReportTargetKind.CHEER, 0, ReportReason.SPAM, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
