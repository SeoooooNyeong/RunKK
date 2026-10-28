package com.a207.runkk.app.domain.chat

import com.a207.runkk.app.core.model.Outcome

/** 크루 채팅 REST 포트(F.2, FR-208~FR-213). 구현 api.chat.ChatRepositoryImpl. 거부는 AppError.Rejected(사유 코드)로 온다. */
interface ChatRepository {
    /** 보내기(POST /crews/{id}/chat/messages). 같은 [clientMessageId] 재전송은 서버가 처음 만든 메시지를 돌려준다(D-101). */
    suspend fun post(crewId: Long, clientMessageId: String, content: String): Outcome<ChatMessage>

    /** 조회(GET /crews/{id}/chat/messages). [before]·[after]는 메시지 ID 커서이고 둘 중 하나만 쓴다. 기본 50, 최대 100. */
    suspend fun history(crewId: Long, before: Long?, after: Long?, size: Int): Outcome<ChatPage>

    /** 리액션 더하기(PUT /chat-messages/{id}/reactions/{kind}, 멱등). */
    suspend fun react(messageId: Long, kind: ReactionKind): Outcome<ChatReactions>

    /** 리액션 빼기(DELETE /chat-messages/{id}/reactions/{kind}, 멱등). */
    suspend fun unreact(messageId: Long, kind: ReactionKind): Outcome<ChatReactions>

    /** 삭제(DELETE /chat-messages/{id}). 본인 또는 운영진 이상만(FR-213). */
    suspend fun delete(messageId: Long): Outcome<Unit>
}
