package com.a207.runkk.app.api.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 채팅 메시지 한 줄(서버 ChatController.ChatMessageItem). authorId는 탈퇴한 작성자면 null, clientMessageId는 본인 메시지만 온다.
 * reactions·myReactions의 키는 서버 ReactionKind 이름이다. 서버 JSON 키 mine은 Kotlin에서 isMine이다(D-111, D-97).
 */
@Serializable
internal data class ChatMessageItem(
    val messageId: Long,
    val authorId: Long? = null,
    val authorName: String,
    val content: String,
    val sentAt: String,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet(),
    @SerialName("mine") val isMine: Boolean = false,
    val canDelete: Boolean = false,
    val clientMessageId: String? = null
)
