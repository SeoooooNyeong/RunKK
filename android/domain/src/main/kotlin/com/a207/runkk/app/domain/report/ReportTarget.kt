package com.a207.runkk.app.domain.report

/** 신고 대상(FR-214): 종류와 대상 ID(게시글 ID 또는 채팅 메시지 ID). */
data class ReportTarget(val kind: ReportTargetKind, val targetId: Long)
