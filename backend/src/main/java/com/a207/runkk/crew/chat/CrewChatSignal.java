package com.a207.runkk.crew.chat;

/**
 * 서버 → 앱 채팅 실시간 메시지(설계 6.1.7a, 5.7.2, D-101). 구현 record는 crew.chat.live에 두고 realtime은 type()으로만
 * 직렬화한다(battle.BattleMessage·crew.CrewRunMessage와 같은 규칙, D-06).
 */
public interface CrewChatSignal {

    /** JSON type 필드 값(record 이름). */
    String type();
}
