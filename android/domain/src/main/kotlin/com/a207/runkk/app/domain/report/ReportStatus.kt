package com.a207.runkk.app.domain.report

/** 신고 처리 상태(서버 report.ReportStatus, FR-214, FR-215). */
enum class ReportStatus {
    RECEIVED,
    AUTO_HIDDEN,
    HIDDEN,
    DISMISSED
}
