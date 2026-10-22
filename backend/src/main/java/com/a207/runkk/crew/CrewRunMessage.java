package com.a207.runkk.crew;

/**
 * 서버 → 앱 크루런 실시간 메시지(설계 6.1.7, D-06). 구현 record 4종(CrewRunStart, CrewRunCancelled, CrewRunRelay,
 * CrewRunEnded)은 crew.live에 있고 sealed를 쓰지 않는다. realtime은 type() 값만 보고 나머지 구성요소를 그대로 직렬화한다.
 */
public interface CrewRunMessage {

    /** JSON의 type 필드 값(record 이름). */
    String type();
}
