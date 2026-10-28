package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.report.ReportReason

/**
 * 채팅방 화면 상태(6.1.14 ui.chat). [messages]·[hasMore]는 ChatRoom.messages(오래된 → 새 순)를 그대로 옮긴다.
 * 글자 수는 보이는 글자(코드 포인트)로 센다(FR-208, D-143). 신고 대화상자는 [reportTarget]이 있을 때 열린다.
 */
data class ChatUiState(
    val crewId: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
    /** 이전 쪽이 더 있는가(위로 끌면 읽는다). */
    val hasMore: Boolean = false,
    val draft: String = "",
    /** 지울지 확인 중인 메시지(FR-213). */
    val confirmDelete: ChatMessage? = null,
    /** 신고 중인 메시지(FR-214). */
    val reportTarget: ChatMessage? = null,
    val reportReason: ReportReason? = null,
    val reportDetail: String = "",
    val isReporting: Boolean = false,
    val message: UiText? = null
) {
    /** 앞뒤 공백을 뺀 보낼 내용의 글자 수(코드 포인트). */
    val draftLength: Int get() = draft.trim().let { it.codePointCount(0, it.length) }

    val isOverLimit: Boolean get() = draftLength > CONTENT_MAX

    /** 1~500자만 보낸다(공백만은 빈 메시지, FR-208). */
    val canSend: Boolean get() = draftLength in 1..CONTENT_MAX

    /** 기타 사유면 상세 내용이 1자 이상 필요하다(FR-214, D-149). */
    val isReportDetailMissing: Boolean get() = reportReason == ReportReason.OTHER && reportDetail.isBlank()

    val canSubmitReport: Boolean get() = reportReason != null && !isReportDetailMissing && !isReporting

    companion object {
        /** 메시지 상한(FR-208 500자 제안 값, 코드 포인트). */
        const val CONTENT_MAX = 500

        /** 신고 상세 상한(FR-214 200자 제안 값, 코드 포인트). */
        const val REPORT_DETAIL_MAX = 200
    }
}
