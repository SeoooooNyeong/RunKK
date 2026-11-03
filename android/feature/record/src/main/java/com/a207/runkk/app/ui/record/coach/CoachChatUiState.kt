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
    val canAsk: Boolean get() = !isSending && failure != CoachFailure.CONSENT_REQUIRED &&
        failure != CoachFailure.QUOTA && failure != CoachFailure.FEATURE_CLOSED && failure != CoachFailure.EXPIRED

    companion object {
        /** FR-261의 길이 제안값. 전송 계약이 확정되면 서버와 함께 대조한다. */
        const val MAX_QUESTION_LENGTH = 500

        fun accepts(text: String): Boolean = text.isNotBlank() &&
            text.codePointCount(0, text.length) <= MAX_QUESTION_LENGTH
    }
}
