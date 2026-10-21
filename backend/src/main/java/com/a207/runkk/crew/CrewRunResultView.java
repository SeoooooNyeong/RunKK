package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.util.List;

/**
 * 크루런 결과(자료사전 크루런결과, FR-191). 순위별 표시명·기록·평균 페이스, 열람자의 첫 크루런 여부, 크루 반영 거리 합.
 *
 * @param postId 크루런 게시글 ID
 * @param status 크루런 상태
 * @param finisherCount 완주 인원
 * @param runners 진행 참가자(순위순, 미완주는 끝)
 * @param firstCrewRun 열람자의 첫 크루런인가
 * @param crewContribution 크루 주간 거리에 반영된 거리 합
 */
public record CrewRunResultView(
        long postId,
        CrewRunStatus status,
        int finisherCount,
        List<Line> runners,
        boolean firstCrewRun,
        Distance crewContribution) {

    public CrewRunResultView {
        runners = List.copyOf(runners);
    }

    /**
     * 참가자 한 명의 결과.
     *
     * @param rank 순위, 미완주·그만둠이면 null
     * @param name 표시명
     * @param elapsed 기록(연결된 러닝의 경과 시간), 없으면 null
     * @param averagePace 평균 페이스, 없으면 null
     * @param quit 중간에 그만뒀는가
     */
    public record Line(
            Integer rank, DisplayName name, Duration elapsed, Pace averagePace, boolean quit) {}
}
