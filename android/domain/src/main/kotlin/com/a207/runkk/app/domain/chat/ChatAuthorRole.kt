package com.a207.runkk.app.domain.chat

/**
 * 채팅 작성자의 지금 크루 역할(서버 응답 authorRole, contract-W5). 서버 crew.CrewRole과 같은 상수다. domain.chat은 domain.crew를
 * 쓰지 않으므로 따로 둔다. 화면은 [LEADER]에만 「크루장」 배지를 붙인다(C5·C7).
 */
enum class ChatAuthorRole {
    LEADER,
    MANAGER,
    MEMBER
}
