package com.a207.runkk.app.domain.chat

/** 리액션 요청의 응답(서버 ReactionResponse, FR-210): 메시지의 종류별 수와 본인이 누른 종류. */
data class ChatReactions(val messageId: Long, val reactions: Map<ReactionKind, Int>, val myReactions: Set<ReactionKind>)
