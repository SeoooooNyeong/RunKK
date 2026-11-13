package com.a207.runkk.app.api.chat

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.chat.ChatAuthorRole
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatMessageKind
import com.a207.runkk.app.domain.chat.ChatPage
import com.a207.runkk.app.domain.chat.ChatReactions
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor

// 채팅 DTO → 도메인 매퍼(6.1.14 api.chat). 앱이 모르는 리액션 종류는 건너뛴다. 시각은 오프셋 있는 값과 없는 값을 모두 읽는다.

internal fun ChatPageResponse.toDomain(): ChatPage = ChatPage(messages.map { it.toDomain() }, hasMore)

internal fun ChatMessageItem.toDomain(): ChatMessage = ChatMessage(
    messageId = messageId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    sentAt = ServerTime.parse(sentAt),
    reactions = reactions.byKind(),
    myReactions = myReactions.mapNotNull { enumByName<ReactionKind>(it) }.toSet(),
    isMine = isMine,
    canDelete = canDelete,
    clientMessageId = clientMessageId,
    kind = enumByName<ChatMessageKind>(messageKind) ?: ChatMessageKind.TEXT,
    authorRole = authorRole?.let { enumByName<ChatAuthorRole>(it) },
    runDistance = runDistanceMeters?.takeIf { it >= 0 }?.let(::Distance)
)

/** 앱이 모르는 반응 종류의 줄은 건너뛴다. */
internal fun ReactorListResponse.toDomain(): List<Reactor> = reactors.mapNotNull { item ->
    enumByName<ReactionKind>(item.kind)?.let { kind ->
        Reactor(item.memberId, item.displayName, kind, ServerTime.parse(item.reactedAt), item.isMe)
    }
}

internal fun ReactionResponse.toDomain(): ChatReactions = ChatReactions(
    messageId = messageId,
    reactions = reactions.byKind(),
    myReactions = myReactions.mapNotNull { enumByName<ReactionKind>(it) }.toSet()
)

private fun Map<String, Int>.byKind(): Map<ReactionKind, Int> =
    entries.mapNotNull { (kind, count) -> enumByName<ReactionKind>(kind)?.let { it to count } }.toMap()
