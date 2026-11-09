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
    val conversationError: UiText? = null
) {
    val canAsk: Boolean get() = !isSending && !isLoadingConversations && conversationError == null &&
        failure != CoachFailure.CONSENT_REQUIRED &&
        failure != CoachFailure.QUOTA && failure != CoachFailure.FEATURE_CLOSED && failure != CoachFailure.EXPIRED

    companion object {
        /** FR-261과 #241 서버 계약의 질문 상한. */
        const val MAX_QUESTION_LENGTH = 500

        fun accepts(text: String): Boolean = text.isNotBlank() &&
            text.codePointCount(0, text.length) <= MAX_QUESTION_LENGTH
    }
}
