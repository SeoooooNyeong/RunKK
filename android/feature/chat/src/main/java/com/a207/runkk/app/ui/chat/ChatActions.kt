package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.report.ReportReason

/** 채팅방 화면의 입력 묶음(상태 없는 ChatRoomScreen을 시험·미리보기에서 그대로 쓴다). */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRetry: (ChatMessage) -> Unit = {},
    val onReaction: (ChatMessage, ReactionKind) -> Unit = { _, _ -> },
    val onLoadOlder: () -> Unit = {},
    val onDeleteAsk: (ChatMessage) -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onReportAsk: (ChatMessage) -> Unit = {},
    val onReportReason: (ReportReason) -> Unit = {},
    val onReportDetail: (String) -> Unit = {},
    val onReportDismiss: () -> Unit = {},
    val onReportSubmit: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** 반응 알약을 길게 누름: 그 종류 탭으로 반응한 사람 시트를 연다(D-656). */
    val onOpenReactors: (ChatMessage, ReactionKind?) -> Unit = { _, _ -> },
    val onReactorTab: (ReactionKind?) -> Unit = {},
    val onCloseReactors: () -> Unit = {}
)
