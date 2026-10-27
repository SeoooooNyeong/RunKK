package com.a207.runkk.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 6.1.12a ReportCase, 6.2 A-24. TC-6128(addReport 기초 경로 C1~C3), TC-6129(상태 전이). */
@DisplayName("report ReportCase")
class ReportCaseTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 23, 9, 0);
    private static final LocalDateTime N = T0.plusHours(2);

    /** 열린 신고자 수 n인 RECEIVED 사례(자동 숨김 없이 n번 반영). */
    private static ReportCase received(int n) {
        ReportCase c = ReportCase.open(ReportTargetKind.CHEER, 100, 7, T0);
        for (int i = 0; i < n && i < ReportCase.AUTO_HIDE_REPORTERS - 1; i++) {
            c.addReport();
        }
        return c;
    }

    private static ReportCase autoHidden() {
        ReportCase c = received(2);
        assertThat(c.addReport()).isTrue();
        return c;
    }

    @Test
    @DisplayName("새 사례는 RECEIVED, 열린 신고자 0, openedAt = 연 시각")
    void open_startsReceived() {
        ReportCase c = ReportCase.open(ReportTargetKind.CHAT_MESSAGE, 501, 7, T0);
        assertThat(c.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(c.getOpenReporterCount()).isZero();
        assertThat(c.getTargetKind()).isEqualTo(ReportTargetKind.CHAT_MESSAGE);
        assertThat(c.getTargetId()).isEqualTo(501);
        assertThat(c.getCrewId()).isEqualTo(7);
        assertThat(c.getOpenedAt()).isEqualTo(T0);
        assertThat(ReportCase.AUTO_HIDE_REPORTERS).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-6128: RECEIVED 1→2 거짓, 2→3 참(AUTO_HIDDEN), AUTO_HIDDEN 3→4 거짓, HIDDEN 0→1 거짓")
    void addReport_basicPaths() {
        ReportCase one = received(1);
        assertThat(one.addReport()).isFalse();
        assertThat(one.getOpenReporterCount()).isEqualTo(2);
        assertThat(one.getStatus()).isEqualTo(ReportStatus.RECEIVED);

        assertThat(one.addReport()).isTrue();
        assertThat(one.getOpenReporterCount()).isEqualTo(3);
        assertThat(one.getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);

        assertThat(one.addReport()).isFalse();
        assertThat(one.getOpenReporterCount()).isEqualTo(4);
        assertThat(one.getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);

        ReportCase hidden = received(1);
        hidden.hide("op-1", N);
        assertThat(hidden.addReport()).isFalse();
        assertThat(hidden.getOpenReporterCount()).isEqualTo(1);
        assertThat(hidden.getStatus()).isEqualTo(ReportStatus.HIDDEN);
    }

    @Test
    @DisplayName(
            "TC-6129 ①②: RECEIVED → hide는 HIDDEN, AUTO_HIDDEN → dismiss는 DISMISSED. 처리 기록·열린 신고자 0")
    void hideAndDismiss_recordResolution() {
        ReportCase r = received(1);
        r.hide("op-1", N);
        assertThat(r.getStatus()).isEqualTo(ReportStatus.HIDDEN);
        assertThat(r.getResolvedAt()).isEqualTo(N);
        assertThat(r.getResolverId()).isEqualTo("op-1");
        assertThat(r.getOpenReporterCount()).isZero();

        ReportCase a = autoHidden();
        a.dismiss("op-1", N);
        assertThat(a.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(a.getResolvedAt()).isEqualTo(N);
        assertThat(a.getResolverId()).isEqualTo("op-1");
        assertThat(a.getOpenReporterCount()).isZero();
    }

    @Test
    @DisplayName("TC-6129 ③④: 이미 HIDDEN·DISMISSED인 사례의 hide·dismiss는 REPORT_DECIDED")
    void decidedCase_rejectsAnotherDecision() {
        ReportCase hidden = received(1);
        hidden.hide("op-1", N);
        assertThatThrownBy(() -> hidden.hide("op-2", N))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.REPORT_DECIDED));
        assertThatThrownBy(() -> hidden.dismiss("op-2", N))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.REPORT_DECIDED));
        assertThat(hidden.getResolverId()).isEqualTo("op-1");

        ReportCase dismissed = received(1);
        dismissed.dismiss("op-1", N);
        assertThatThrownBy(() -> dismissed.dismiss("op-1", N))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.REPORT_DECIDED));
    }

    @Test
    @DisplayName(
            "TC-6129 ⑤: reopen은 DISMISSED만 — RECEIVED·AUTO_HIDDEN·HIDDEN은 IllegalStateException")
    void reopen_onlyFromDismissed() {
        assertThatThrownBy(() -> received(1).reopen(N)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> autoHidden().reopen(N)).isInstanceOf(IllegalStateException.class);
        ReportCase hidden = received(1);
        hidden.hide("op-1", N);
        assertThatThrownBy(() -> hidden.reopen(N)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName(
            "기각 뒤 reopen: RECEIVED, 열린 신고자 0부터, openedAt 갱신, 처리 기록 비움. 그 뒤 신고자만 세어 3명이면 다시 자동 숨김")
    void reopen_countsOnlyNewReporters() {
        ReportCase c = autoHidden();
        c.dismiss("op-1", N);
        LocalDateTime later = N.plusDays(1);
        c.reopen(later);
        assertThat(c.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(c.getOpenReporterCount()).isZero();
        assertThat(c.getOpenedAt()).isEqualTo(later);
        assertThat(c.getResolvedAt()).isNull();
        assertThat(c.getResolverId()).isNull();
        assertThat(c.addReport()).isFalse();
        assertThat(c.addReport()).isFalse();
        assertThat(c.addReport()).isTrue();
        assertThat(c.getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);
    }

    @Test
    @DisplayName("운영자 계정이 비면 IllegalArgumentException")
    void decision_requiresOperator() {
        assertThatThrownBy(() -> received(1).hide(" ", N))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> received(1).dismiss(null, N))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
