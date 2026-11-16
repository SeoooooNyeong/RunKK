package com.a207.runkk.app.api.chat

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatPage
import com.a207.runkk.app.domain.chat.ChatReactions
import com.a207.runkk.app.domain.chat.ChatRepository
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor
import javax.inject.Inject

/**
 * 크루 채팅 포트를 REST로 구현한다(6.1.14 api.chat, FR-208~FR-213, D-101). 거부는 Rejected(CHAT_EMPTY·BODY_TOO_LONG·
 * NOT_CREW_MEMBER·CHAT_MESSAGE_NOT_FOUND 등), 보내기 속도 제한(429 TOO_MANY_ATTEMPTS)은 ApiErrorMapper 규칙을 따른다.
 */
internal class ChatRepositoryImpl
@Inject
constructor(private val api: ChatApi, private val faces: FaceImageSource) :
    ChatRepository {
    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = faces.faceLayers(faceKey)

    override fun cachedFaceLayers(faceKey: String?): List<FaceLayer>? = faces.peek(faceKey)

    override suspend fun post(crewId: Long, clientMessageId: String, content: String): Outcome<ChatMessage> =
        ApiErrorMapper.call { api.post(crewId, ChatPostRequest(clientMessageId, content)).toDomain() }

    override suspend fun history(crewId: Long, before: Long?, after: Long?, size: Int): Outcome<ChatPage> =
        ApiErrorMapper.call { api.history(crewId, before, after, size).toDomain() }

    override suspend fun react(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> =
        ApiErrorMapper.call { api.react(messageId, kind.name).toDomain() }

    override suspend fun unreact(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> =
        ApiErrorMapper.call { api.unreact(messageId, kind.name).toDomain() }

    override suspend fun reactors(messageId: Long): Outcome<List<Reactor>> =
        ApiErrorMapper.call { api.reactors(messageId).toDomain() }

    override suspend fun delete(messageId: Long): Outcome<Unit> = ApiErrorMapper.call { api.delete(messageId) }
}
