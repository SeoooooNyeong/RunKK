package com.a207.runkk.realtime;

import com.a207.runkk.crew.chat.live.CrewChatPosted;

/** ArchUnit 음성 시험 전용(규칙 7 위반, TC-6209 ④, TC-6832). realtime은 crew.chat.live 형식을 import하지 않는다. */
public class ArchFixtureRealtimeUsesChatLive {
    public long idOf(CrewChatPosted message) {
        return message.messageId();
    }
}
