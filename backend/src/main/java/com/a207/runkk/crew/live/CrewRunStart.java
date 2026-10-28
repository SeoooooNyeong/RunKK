package com.a207.runkk.crew.live;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.crew.CrewRunMessage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 시작 신호(설계 5.7.2, FR-148). 준비 완료한 참가자에게만 보낸다. 앱은 3초 카운트다운 뒤 startAt에 측정을 자동 시작한다.
 *
 * @param postId 크루런 게시글 ID
 * @param startAt 시작 일시(모집희망일시 + 3초)
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
