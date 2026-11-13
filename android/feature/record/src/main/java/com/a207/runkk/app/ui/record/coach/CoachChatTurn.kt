package com.a207.runkk.app.ui.record.coach

import com.a207.runkk.app.domain.coach.CoachMessage

data class CoachChatTurn(val question: String, val answer: String = "", val isComplete: Boolean = false)

/**
 * 지난 문답(오래된 순)을 질문·답 한 쌍씩 묶는다(S15P31A207-300). 답을 받지 못한 질문은 답 없이 남기고,
 * 앞 질문이 조회 상한으로 잘린 답은 질문 없이 코치 말풍선만 보인다.
 */
internal fun List<CoachMessage>.toChatTurns(): List<CoachChatTurn> = fold(emptyList()) { turns, message ->
    val last = turns.lastOrNull()
    when {
        message.role == CoachMessage.Role.QUESTION -> turns + CoachChatTurn(message.text)
        last != null && last.answer.isEmpty() -> turns.dropLast(1) + last.copy(answer = message.text, isComplete = true)
        else -> turns + CoachChatTurn("", message.text, isComplete = true)
    }
}
