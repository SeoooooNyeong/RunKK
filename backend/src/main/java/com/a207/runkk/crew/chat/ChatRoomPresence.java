package com.a207.runkk.crew.chat;

import java.util.Set;

/**
 * 채팅방 입장 상태 포트(설계 6.1.7a, D-101). 구현은 realtime.ChatPresenceRegistry(메모리, 소켓이 닫히면 지움, api 1개 전제)와
 * PollingChatPresence(늘 빈 집합)다.
 */
public interface ChatRoomPresence {

    /** 그 크루 채팅방 화면을 연 회원. */
    Set<Long> presentMembers(long crewId);
}
