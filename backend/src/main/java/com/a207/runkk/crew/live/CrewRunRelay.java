package com.a207.runkk.crew.live;

import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.RelayEntry;
import java.util.List;

/**
 * 크루런 진행 중계(합산 방식, S15P31A207-236, FR-149). 크루가 함께 달린 거리 합과 참가자마다 표시명·누적 거리·진행 상태를 담고 위도·경도· 순위가
 * 없다. 앱은 거리 차로 내 경로 위에 크루원 아바타를 놓는다.
 *
 * @param postId 크루런 게시글 ID
 * @param totalDistance 진행 참가자 전원(끝낸 사람 포함)의 최신 거리 합
 * @param entries 진행 참가자, 대기실 입장 순
 */
public record CrewRunRelay(long postId, Distance totalDistance, List<RelayEntry> entries)
        implements CrewRunMessage {

    public CrewRunRelay {
        entries = List.copyOf(entries);
    }

    @Override
    public String type() {
        return "CrewRunRelay";
    }

    /** 받는 사람 본인 줄에만 mine 참(D-161). 참가자가 아니면 모두 거짓이다. */
    @Override
    public CrewRunRelay forRecipient(long memberId) {
        return new CrewRunRelay(
                postId, totalDistance, entries.stream().map(e -> e.seenBy(memberId)).toList());
    }
}
