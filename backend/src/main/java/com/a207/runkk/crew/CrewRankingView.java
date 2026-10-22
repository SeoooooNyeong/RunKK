package com.a207.runkk.crew;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.util.List;

/**
 * 크루 내 랭킹(자료사전 크루내랭킹, FR-137). 기록 공개 범위가 비공개인 크루원도 순위와 누적 거리만 보인다.
 *
 * @param crewId 크루 ID
 * @param period 집계 기간
 * @param entries 누적 유효 거리 큰 순
 */
public record CrewRankingView(long crewId, AggregationPeriod period, List<Line> entries) {

    public CrewRankingView {
        entries = List.copyOf(entries);
    }

    /**
     * 순위 한 줄.
     *
     * @param rank 순위(같은 거리는 같은 순위)
     * @param memberId 회원 ID
     * @param name 표시명
     * @param distance 기간 안 유효 거리 합
     */
    public record Line(int rank, long memberId, DisplayName name, Distance distance) {}
}
