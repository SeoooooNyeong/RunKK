package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor
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
    val message: UiText? = null,
    /** 반응한 사람 시트를 연 메시지(피그마 F-09d). null이면 닫힘. */
    val reactorsTarget: ChatMessage? = null,
    /** 시트에서 고른 종류 탭. null = 전체. */
    val reactorsTab: ReactionKind? = null,
    /** 그 메시지의 반응한 사람 전체(먼저 단 순서). 탭은 [visibleReactors]가 거른다. */
    val reactors: List<Reactor> = emptyList(),
    val isLoadingReactors: Boolean = false,
    val isReactorsFailed: Boolean = false
) {
    /** 고른 탭의 반응한 사람. */
    val visibleReactors: List<Reactor> get() = reactorsTab?.let { tab -> reactors.filter { it.kind == tab } }
        ?: reactors

    /**
     * 시트 탭의 종류별 수. 읽은 목록이 있으면 그 목록으로 세고(시트를 연 뒤 실시간으로 바뀐 수와 목록이 어긋나지 않게), 읽기 전이면 메시지의
     * 종류별 수다.
     */
    val reactorCounts: Map<ReactionKind, Int>
        get() = if (reactors.isNotEmpty()) {
            reactors.groupingBy { it.kind }.eachCount()
        } else {
            reactorsTarget?.reactions.orEmpty()
        }

    /** 시트 탭 줄의 종류(수가 있는 종류, 선언 순서). 「전체」 탭은 화면이 앞에 붙인다. */
    val reactorTabs: List<ReactionKind> get() = ReactionKind.entries.filter { (reactorCounts[it] ?: 0) > 0 }

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
