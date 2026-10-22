package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.TagSet;

/**
 * 크루 한 곳의 정보(크루 홈·정보 수정 화면, FR-113, FR-117). 크루 밖 러너도 공개 크루면 볼 수 있는 항목(Q-45, W-16)에 열람자 역할을 더했다. 초대
 * 코드는 크루장에게만 담는다(FR-112, FR-114). 설계 5.7.1에 없던 조회라 B 단계에서 더했다(앱–서버 빈 곳).
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 성향 태그
 * @param visibility 공개 여부
 * @param joinPolicy 가입 방식
 * @param logoUrl 로고, 없으면 null
 * @param leaderId 크루장 회원 ID
 * @param leaderName 크루장 표시명
 * @param headcount 인원
 * @param weeklyDistance 이번 주 크루 거리
 * @param myRole 열람자 역할, 크루 밖이면 null
 * @param inviteCode 초대 코드, 크루장이 아니면 null
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
        CrewRole myRole,
        InviteCode inviteCode) {}
