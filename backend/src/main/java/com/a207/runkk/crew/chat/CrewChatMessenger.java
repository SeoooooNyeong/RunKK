package com.a207.runkk.crew.chat;

import java.util.Collection;

/**
 * 채팅 실시간 송신 포트(설계 6.1.7a, D-06, D-101). 구현은 realtime.WebSocketMessenger와 PollingMessenger(아무것도 하지
 * 않음)다. 연결이 없으면 버린다(우편함에 넣지 않는다). 앱은 재연결 뒤 GET …/chat/messages?after=로 메운다.
 */
public interface CrewChatMessenger {

    void send(Collection<Long> memberIds, CrewChatSignal signal);
}
