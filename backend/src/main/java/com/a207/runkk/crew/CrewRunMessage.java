package com.a207.runkk.crew;

/**
 * 서버 → 앱 크루런 실시간 메시지(설계 6.1.7, D-06). 구현 record 4종(CrewRunStart, CrewRunCancelled, CrewRunRelay,
 * CrewRunEnded)은 crew.live에 있고 sealed를 쓰지 않는다. realtime은 type() 값만 보고 나머지 구성요소를 그대로 직렬화한다.
 */
public interface CrewRunMessage {

    /** JSON의 type 필드 값(record 이름). */
    String type();

    /**
     * 받는 사람 memberId에게 보낼 모양. 받는 사람마다 내용이 다른 메시지(CrewRunRelay의 mine, D-161)만 바꾸고 나머지는 그대로다. 송신 구현은
     * 회원마다 이 결과를 직렬화한다(크루 최대 10명).
     */
    default CrewRunMessage forRecipient(long memberId) {
        return this;
    }
}
