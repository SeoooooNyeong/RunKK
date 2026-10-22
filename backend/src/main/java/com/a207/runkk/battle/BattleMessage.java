package com.a207.runkk.battle;

/**
 * 서버 → 앱 대결 실시간 메시지(설계 6.1.6, D-06). 구현 record 6종은 battle.live에 있고 sealed를 쓰지 않는다. realtime은 type()
 * 값만 보고 나머지 구성요소를 그대로 직렬화한다.
 */
public interface BattleMessage {

    /** JSON의 type 필드 값(record 이름). */
    String type();
}
