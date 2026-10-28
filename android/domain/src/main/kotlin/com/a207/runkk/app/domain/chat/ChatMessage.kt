package com.a207.runkk.app.domain.chat

import java.time.Instant

/**
 * 크루 채팅 메시지 한 건(FR-208~FR-213). 위치 항목이 없다(NFR-021).
 * - [messageId]: 서버 메시지 ID. 서버가 받기 전의 낙관적 항목이면 null이고 [clientMessageId]로 찾는다(D-101).
 * - [isRemoved]: 삭제·숨김된 메시지의 "삭제된 메시지" 자리표시다. 내용·리액션은 비운다(D-160 (1)).
 * - [sendState]: 내가 보낸 메시지의 전송 상태. 실패면 ChatRoom.retry로 같은 clientMessageId로 다시 보낸다(D-160 (3)).
 */
data class ChatMessage(
    val messageId: Long?,
    val authorId: Long?,
    val authorName: String,
    val content: String,
    val sentAt: Instant,
    val reactions: Map<ReactionKind, Int>,
    val myReactions: Set<ReactionKind>,
    val isMine: Boolean,
    val canDelete: Boolean,
    val clientMessageId: String?,
    val isRemoved: Boolean = false,
    val sendState: SendState = SendState.SENT
) {
    enum class SendState {
        SENT,
        SENDING,
        FAILED
    }

    /** 삭제·숨김 자리표시로 바꾼 사본. 순서·ID는 그대로 둔다. */
    fun removed(): ChatMessage =
        copy(content = "", reactions = emptyMap(), myReactions = emptySet(), canDelete = false, isRemoved = true)
}
