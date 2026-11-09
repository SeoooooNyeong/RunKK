package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 결과(합산 방식, S15P31A207-236, FR-191 대체). 크루가 함께 달린 거리 합, 참가자별 거리·시간·평균 페이스(입장 순), 열람자의 연결 기록, 첫
 * 크루런 여부, 크루 반영 거리 합.
 *
 * @param postId 크루런 게시글 ID
 * @param status 크루런 상태
 * @param title 모집글 본문 첫 줄
 * @param distance 크루런 거리(목표)
 * @param startedAt 출발 일시, 출발 전이면 null
 * @param totalDistance 참가자 거리 합
 * @param participants 진행 참가자(대기실 입장 순)
 * @param myRecordId 열람자의 연결된 기록 ID, 없으면 null
 * @param firstCrewRun 열람자의 첫 크루런인가
 * @param crewContribution 크루 주간 거리에 반영된 거리 합
 */
public record CrewRunResultView(
        long postId,
        CrewRunStatus status,
        String title,
        StandardDistance distance,
        LocalDateTime startedAt,
        Distance totalDistance,
        List<Line> participants,
        String myRecordId,
        boolean firstCrewRun,
        Distance crewContribution) {

    public CrewRunResultView {
        participants = List.copyOf(participants);
    }

    /**
     * 참가자 한 명의 결과. 값은 연결된 기록 → 끝낼 때 남긴 값 순으로 고른다.
     *
     * @param name 표시명
     * @param distance 거리, 값이 없으면 0
     * @param elapsed 시간, 없으면 null
     * @param averagePace 평균 페이스, 없으면 null
     * @param ended 끝냈는가
     * @param memberId 참가자 회원 ID, 탈퇴했으면 null(앱이 본인 줄을 가린다)
     * @param shownGender 표시 성별 이름(MALE·FEMALE, D-327), 탈퇴했으면 null(크루원 아바타, S15P31A207-236)
     */
    public record Line(
            DisplayName name,
            Distance distance,
            Duration elapsed,
            Pace averagePace,
            boolean ended,
            Long memberId,
            String shownGender) {}
}
