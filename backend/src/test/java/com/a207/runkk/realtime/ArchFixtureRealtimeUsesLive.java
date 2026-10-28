package com.a207.runkk.realtime;

import com.a207.runkk.battle.live.ArchFixtureLiveMessage;

/** ArchUnit 음성 시험 전용(규칙 7 위반). realtime은 battle.live 메시지 형식을 import하지 않는다(D-06). */
public class ArchFixtureRealtimeUsesLive {
    public long idOf(ArchFixtureLiveMessage message) {
        return message.battleId();
    }
}
