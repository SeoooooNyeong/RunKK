package com.a207.runkk.app.domain.chat

import java.time.Instant

/**
 * 메시지에 반응한 사람 한 줄(GET /chat-messages/{id}/reactions, 피그마 F-09d). 반응 한 건이 한 줄이라 두 종류를 단 회원은 두 줄이다.
 * 목록은 먼저 단 순서다.
 */
data class Reactor(
    val memberId: Long,
    val displayName: String,
    val kind: ReactionKind,
    val reactedAt: Instant,
    val isMe: Boolean
)
