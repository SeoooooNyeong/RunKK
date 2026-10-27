package com.a207.runkk.realtime;

/**
 * 앱 → 서버 채팅방 입장 신호의 전송 형식(설계 6.1.13, 5.7.2, F.3.2). 채팅방 화면이 보이면 present 참, 떠나면 거짓이고 앱이 재연결마다 다시
 * 보낸다. 위치 항목이 없다(NFR-021).
 *
 * @param crewId 크루 ID
 * @param present 방 화면이 보임
 */
public record InboundChatPresence(long crewId, boolean present) implements InboundMessage {}
