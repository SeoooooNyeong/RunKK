package com.a207.runkk.app.ui.record.coach

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.coach.CoachConversation
import com.a207.runkk.app.domain.coach.CoachFailure

data class CoachChatUiState(
    val conversationId: String? = null,
    val turns: List<CoachChatTurn> = emptyList(),
    val isSending: Boolean = false,
    val failure: CoachFailure? = null,
    val inputError: UiText? = null,
    val conversations: List<CoachConversation> = emptyList(),
    val isLoadingConversations: Boolean = false,
    val conversationError: UiText? = null,
    /** 이전 대화를 열었을 때 서버에서 읽은 지난 문답(S15P31A207-300). 새로 묻는 문답은 [turns]에 쌓는다. */
    val history: List<CoachChatTurn> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historyError: UiText? = null
) {
    // 지난 문답을 다 읽기 전에는 묻지 않는다. 새 문답이 지난 문답보다 위에 그려지지 않게 한다.
    val canAsk: Boolean get() = !isSending && !isLoadingConversations && conversationError == null &&
        !isLoadingHistory && historyError == null &&
        failure != CoachFailure.CONSENT_REQUIRED &&
        failure != CoachFailure.QUOTA && failure != CoachFailure.FEATURE_CLOSED && failure != CoachFailure.EXPIRED

    companion object {
        /** FR-261과 #241 서버 계약의 질문 상한. */
        const val MAX_QUESTION_LENGTH = 500

        fun accepts(text: String): Boolean = text.isNotBlank() &&
            text.codePointCount(0, text.length) <= MAX_QUESTION_LENGTH
    }
}
