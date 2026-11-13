package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;

/**
 * 크루런 중계 한 줄(합산 방식, S15P31A207-236): 표시명 + 누적 거리 + 진행 상태. 위치·순위 항목이 없다(FR-149). JSON 항목 이름은 구성요소
 * 이름과 같다(계약 시험, H-17).
 *
 * @param name 표시명
 * @param distance 누적 거리
 * @param state 진행 상태
 * @param memberId 참가자 회원 ID(앱이 자기 줄과 지도 위 크루원 아바타를 가린다)
 * @param mine 받는 사람 본인의 줄이면 참(D-161). 세션은 거짓으로 만들고 보낼 때 받는 사람마다 정한다(CrewRunRelay.forRecipient)
 */
public record RelayEntry(
        DisplayName name, Distance distance, State state, long memberId, boolean mine) {

    /** 받는 사람 recipientId에게 보일 줄: 자기 줄이면 mine 참. */
    public RelayEntry seenBy(long recipientId) {
        return new RelayEntry(name, distance, state, memberId, memberId == recipientId);
    }

    /** 참가자 진행 상태. */
    public enum State {
        /** 달리는 중. */
        RUNNING,
        /** 일시정지. */
        PAUSED,
        /** 3초 넘게 진행값이 없다(연결 확인 중). */
        CHECKING,
        /** 자기 크루런을 끝냈다. */
        ENDED
    }
}
