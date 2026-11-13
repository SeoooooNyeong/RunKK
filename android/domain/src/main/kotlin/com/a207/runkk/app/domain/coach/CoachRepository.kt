package com.a207.runkk.app.domain.coach

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/** AI 코치 질문과 대화 색인 계약(D-453). 본문은 ai 서버가 보관한다. */
interface CoachRepository {
    fun ask(conversationId: String?, text: String): Flow<CoachEvent>

    suspend fun conversations(): Outcome<List<CoachConversation>>

    /** 이전 대화를 열 때 지난 문답을 오래된 순으로 읽는다(S15P31A207-300). 지났거나 남의 대화면 CONVERSATION_EXPIRED로 거절된다. */
    suspend fun messages(conversationId: String): Outcome<List<CoachMessage>>
}
