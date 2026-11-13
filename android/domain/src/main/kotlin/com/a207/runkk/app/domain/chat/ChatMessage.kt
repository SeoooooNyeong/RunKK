package com.a207.runkk.app.domain.chat

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 크루 채팅 메시지 한 건(FR-208~FR-213). 위치 항목이 없다(NFR-021).
 * - [messageId]: 서버 메시지 ID. 서버가 받기 전의 낙관적 항목이면 null이고 [clientMessageId]로 찾는다(D-101).
 * - [isRemoved]: 삭제·숨김된 메시지의 "삭제된 메시지" 자리표시다. 내용·리액션은 비운다(D-160 (1)).
 * - [sendState]: 내가 보낸 메시지의 전송 상태. 실패면 ChatRoom.retry로 같은 clientMessageId로 다시 보낸다(D-160 (3)).
 * - [kind]: 보통 글(TEXT) 또는 러닝 완료 시스템 메시지(RUN_FINISHED, contract-W5). 시스템 메시지는 [runDistance]가 있다.
 * - [authorRole]: 작성자의 지금 크루 역할. 탈퇴했거나 크루를 떠났으면 null(크루장 배지, C7).
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
    val sendState: SendState = SendState.SENT,
    val kind: ChatMessageKind = ChatMessageKind.TEXT,
    val authorRole: ChatAuthorRole? = null,
    val runDistance: Distance? = null
) {
    /** 러닝 완료 같은 서버 시스템 메시지인가. */
    val isSystem: Boolean get() = kind != ChatMessageKind.TEXT

    /** 「크루장」 배지를 붙이는가(보통 글의 크루장만). */
    val isLeaderBadge: Boolean get() = !isSystem && authorRole == ChatAuthorRole.LEADER

    enum class SendState {
        SENT,
        SENDING,
        FAILED
    }

    /** 삭제·숨김 자리표시로 바꾼 사본. 순서·ID는 그대로 둔다. */
    fun removed(): ChatMessage =
        copy(content = "", reactions = emptyMap(), myReactions = emptySet(), canDelete = false, isRemoved = true)
}
