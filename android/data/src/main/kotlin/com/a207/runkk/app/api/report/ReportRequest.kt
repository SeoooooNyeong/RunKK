package com.a207.runkk.app.api.report

import kotlinx.serialization.Serializable

/**
 * POST /reports 요청(서버 ReportController.ReportRequest). targetKind·reason은 서버 enum 이름, targetId는 게시글 ID 또는 채팅 메시지 ID,
 * detail은 OTHER면 필수(200자)이고 그 밖에는 비워도 된다. 검사는 서버가 한다.
 */
@Serializable
internal data class ReportRequest(val targetKind: String, val targetId: Long, val reason: String, val detail: String?)
