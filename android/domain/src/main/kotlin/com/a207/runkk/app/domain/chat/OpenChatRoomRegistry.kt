package com.a207.runkk.app.domain.chat

/**
 * 지금 열린 채팅방의 크루를 알리는 포트(D-130). 구현 data.push.OpenChatRoomTracker: 열린 방 크루의 CREW_CHAT 푸시는
 * 표시하지 않는다(FR-218, D-102). ChatRoom.open·close가 부른다.
 */
interface OpenChatRoomRegistry {
    fun opened(crewId: Long)

    fun closed(crewId: Long)
}
