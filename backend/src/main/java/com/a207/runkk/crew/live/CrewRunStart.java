package com.a207.runkk.crew.live;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.crew.CrewRunMessage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 시작 신호(설계 5.7.2, FR-148). 출발이 확정된 진행 참가자에게만 보낸다(설계 05-32: 출발 확정 뒤, 후보에서 빠진 사람은 받지 않는다).
 * startAt은 측정 시작 절대 시각이다.
 *
 * @param postId 크루런 게시글 ID
 * @param startAt 측정 시작 절대 시각(출발 예약 시각 + 3초)
 * @param participantNames 함께 출발하는 참가자 표시명
 */
public record CrewRunStart(long postId, LocalDateTime startAt, List<DisplayName> participantNames)
        implements CrewRunMessage {

    public CrewRunStart {
        participantNames = List.copyOf(participantNames);
    }

    @Override
    public String type() {
        return "CrewRunStart";
    }
}
