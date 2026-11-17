package com.a207.runkk.app.domain.chat

import com.a207.runkk.app.core.model.FaceLayer
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

    /** 반응한 사람(GET /chat-messages/{id}/reactions). 먼저 단 순서이고 종류 탭은 화면이 거른다(피그마 F-09d, contract-W5). */
    suspend fun reactors(messageId: Long): Outcome<List<Reactor>>

    /** 삭제(DELETE /chat-messages/{id}). 본인 또는 운영진 이상만(FR-213). */
    suspend fun delete(messageId: Long): Outcome<Unit>

    /**
     * 작성자·반응자 얼굴 그림: faceKey(셀카 아바타)로 부위 그림을 받는다(CrewRepository.faceLayers와 같은 그림, 같은 캐시).
     * 셀카 얼굴이 아니면 빈 목록, 받지 못하면 실패다. 구현이 없으면 빈 목록이다.
     */
    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = Outcome.Success(emptyList())

    /** 기다리지 않고 바로 그릴 수 있는 얼굴 그림(앞서 받아 둔 것). 없으면 null. */
    fun cachedFaceLayers(faceKey: String?): List<FaceLayer>? = null
}
