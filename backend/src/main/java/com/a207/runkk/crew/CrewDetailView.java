package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.TagSet;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루 한 곳의 정보(크루 홈·정보 수정 화면, FR-113, FR-117). 크루 밖 러너도 공개 크루면 볼 수 있는 항목(Q-45, W-16)에 열람자 역할을 더했다. 초대
 * 코드는 크루원 모두에게 담는다(FR-112, 피그마 F-18b, S15P31A207-236). 재발급(FR-114)은 크루장만 한다. 설계 5.7.1에 없던 조회라 B
 * 단계에서 더했다(앱–서버 빈 곳).
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 크루 태그(요일·시간대)
 * @param visibility 공개 여부
 * @param joinPolicy 가입 방식
 * @param logoUrl 로고, 없으면 null
 * @param leaderId 크루장 회원 ID
 * @param leaderName 크루장 표시명
 * @param headcount 인원
 * @param weeklyDistance 이번 주 크루 거리
 * @param lastWeekDistance 지난 주 크루 거리(크루 상세 통계, 피그마 F-02)
 * @param myRole 열람자 역할, 크루 밖이면 null
 * @param inviteCode 초대 코드, 크루 밖이면 null
 * @param createdAt 개설 일시(피그마 F-02·F-18, S15P31A207-295)
 * @param totalDistance 누적 크루 거리: 모든 주의 크루 주간 거리 합(W7 F-18)
 * @param crewRunCount 누적 크루런 횟수: 마친 크루런 수(지운 글 제외)
 * @param recentCrewRuns 최근 마친 크루런 최대 3건, 약속 시각 최신순(크루 밖도 보는 요약, 결정 C9)
 * @param rejoinAvailableAt 열람자가 24시간 가입 제한 중이면 가입 가능 시각, 아니면 null(F-02c)
 */
public record CrewDetailView(
        long crewId,
        String name,
        String intro,
        TagSet tags,
        CrewVisibility visibility,
        JoinPolicy joinPolicy,
        String logoUrl,
        long leaderId,
        DisplayName leaderName,
        int headcount,
        Distance weeklyDistance,
        Distance lastWeekDistance,
        CrewRole myRole,
        InviteCode inviteCode,
        LocalDateTime createdAt,
        Distance totalDistance,
        int crewRunCount,
        List<RecentCrewRun> recentCrewRuns,
        LocalDateTime rejoinAvailableAt) {

    /** 최근 크루런 상한(피그마 F-02). */
    public static final int RECENT_CREW_RUNS = 3;

    public CrewDetailView {
        recentCrewRuns = List.copyOf(recentCrewRuns);
    }

    /**
     * 마친 크루런 한 건의 요약. 참가자 이름 같은 개인 정보는 없다(결정 C9).
     *
     * @param postId 크루런 모집글 ID
     * @param scheduledAt 약속 시각
     * @param participantCount 함께 달린(출발한) 인원
     * @param totalDistance 참가자 거리 합(결과 화면과 같은 규칙)
     */
    public record RecentCrewRun(
            long postId, LocalDateTime scheduledAt, int participantCount, Distance totalDistance) {}
}
