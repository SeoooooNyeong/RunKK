package com.a207.runkk.app.domain.chat

/** 채팅 메시지 리액션 종류(서버 crew.chat.ReactionKind, FR-210). CHECK는 피그마 맞춤 W5(사용자 결정 C7)에서 더했다. */
enum class ReactionKind {
    LIKE,
    HEART,
    CHECK
}
