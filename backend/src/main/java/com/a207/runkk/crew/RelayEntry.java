package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;

/**
 * 크루런 중계 한 줄(자료사전 크루런중계: 표시명 + 누적거리 + 크루런순위 + 연결상태, 5.7.2 CrewRunRelay.entries). 위치 항목이 없다(FR-149).
 * JSON 항목 이름은 구성요소 이름과 같다(계약 시험, H-17).
 *
 * @param name 표시명
 * @param distance 누적 거리
 * @param rank 순위, 그만뒀으면 null
 * @param connection 연결 상태
 * @param memberId 참가자 회원 ID(앱이 자기 줄을 가려 순위 안내·강조에 쓴다, FR-190). 표본 계약에 뒤늦게 더한 항목이라 끝에 둔다
 * @param mine 받는 사람 본인의 줄이면 참(D-161). 세션 순위는 거짓으로 만들고 보낼 때 받는 사람마다 정한다(CrewRunRelay.forRecipient)
 */
public record RelayEntry(
        DisplayName name,
        Distance distance,
        Integer rank,
        Connection connection,
        long memberId,
        boolean mine) {

    /** 받는 사람 recipientId에게 보일 줄: 자기 줄이면 mine 참. */
    public RelayEntry seenBy(long recipientId) {
        return new RelayEntry(name, distance, rank, connection, memberId, memberId == recipientId);
    }

    /** 연결 상태(5.7.2). */
    public enum Connection {
        CONNECTED,
        CHECKING,
        QUIT,
        ARRIVED
    }
}
