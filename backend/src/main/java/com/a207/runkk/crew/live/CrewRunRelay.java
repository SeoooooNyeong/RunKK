package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.RelayEntry;
import java.util.List;

/**
 * 크루런 진행 중계(설계 5.7.2, FR-149, FR-190). 참가자마다 표시명·누적 거리·순위·연결 상태를 담고 위도·경도·코스가 없다. 앱은 자기 줄의 순위로
 * 1km마다 "n명 중 n위"를 안내한다.
 *
 * @param postId 크루런 게시글 ID
 * @param entries 순위순 참가자, 그만둔 참가자는 끝에 순위 없이
 */
public record CrewRunRelay(long postId, List<RelayEntry> entries) implements CrewRunMessage {

    public CrewRunRelay {
        entries = List.copyOf(entries);
    }

    @Override
    public String type() {
        return "CrewRunRelay";
    }
}
