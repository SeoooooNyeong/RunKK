package com.a207.runkk.app.domain.chat

/**
 * 채팅 메시지 종류(서버 crew.chat.ChatMessageKind, contract-W5). [TEXT]는 크루원이 보낸 글, [RUN_FINISHED]는 크루원이 러닝을 마쳤을 때
 * 서버가 남기는 시스템 메시지(닉네임과 거리만)다.
 */
enum class ChatMessageKind {
    TEXT,
    RUN_FINISHED
}
