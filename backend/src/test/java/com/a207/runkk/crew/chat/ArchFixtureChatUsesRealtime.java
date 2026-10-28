package com.a207.runkk.crew.chat;

import com.a207.runkk.realtime.ChatPresenceRegistry;

/** ArchUnit 음성 시험 전용(규칙 3 위반, TC-6838). crew.chat은 포트 ChatRoomPresence만 알고 realtime 구현을 모른다. */
public class ArchFixtureChatUsesRealtime {
    public int countIn(ChatPresenceRegistry registry, long crewId) {
        return registry.presentMembers(crewId).size();
    }
}
