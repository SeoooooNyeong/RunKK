package com.a207.runkk.battle;

import java.util.Collection;

/**
 * 대결 실시간 송신 포트(설계 6.1.6, D-06). 구현은 realtime.WebSocketMessenger(기본)와
 * realtime.PollingMessenger(runkk.live=polling).
 */
public interface BattleMessenger {

    /** 회원 한 명에게 보낸다. 연결이 없으면 버리지 않고 30초 우편함에 두었다가 재연결 때 전달한다. */
    void send(long memberId, BattleMessage m);

    void sendAll(Collection<Long> memberIds, BattleMessage m);
}
