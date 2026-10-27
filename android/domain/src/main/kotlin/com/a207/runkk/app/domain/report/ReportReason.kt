package com.a207.runkk.app.domain.report

/** 신고 사유(서버 report.ReportReason, FR-214). OTHER면 상세 내용(detail)이 필요하다. */
enum class ReportReason {
    ABUSE,
    SEXUAL,
    SPAM,
    PRIVACY,
    OTHER
}
