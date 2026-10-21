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
 */
public record RelayEntry(DisplayName name, Distance distance, Integer rank, Connection connection) {

    /** 연결 상태(5.7.2). */
    public enum Connection {
        CONNECTED,
        CHECKING,
        QUIT,
        ARRIVED
    }
}
