package com.a207.runkk.profile;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberProfile;

/**
 * 프로필 화면(설계 6.1.5 ProfileView, FR-005, FR-006). 공개 수준(disclosure)이 보이지 않게 정한 항목은 null·빈 값이다.
 * Runimal 유형은 이름 문자열로 내준다(web.me가 runimal을 import하지 않게, 12.2 D46).
 *
 * @param memberId 회원 ID
 * @param disclosure 공개 수준(SELF·PUBLIC·PRIVATE·WITHDRAWN)
 * @param displayName 표시명(탈퇴면 "탈퇴한 사용자")
 * @param faceKey 아바타 얼굴 키, 없으면 null
 * @param avatarBackgroundKey 아바타 랜드마크 배경 키, 기본 배경이면 null. 아바타 얼굴을 보이는 수준(SELF·PUBLIC·PRIVATE)만
 *     싣는다(FR-202)
 * @param tier 티어
 * @param runimalType Runimal 유형 이름, 없으면 null
 * @param crew 소속 크루, 없으면 null
 * @param longestDistance 최장 거리(본인만, FR-076)
 * @param settings 설정(본인만)
 * @param about 레벨·사진·소개·나이대·성별(본인·공개만, 비공개면 감춘다 FR-006)
 */
public record ProfileView(
        long memberId,
        ProfileDisclosure disclosure,
        DisplayName displayName,
        String faceKey,
        String avatarBackgroundKey,
        Tier tier,
        String runimalType,
        Crew crew,
        Distance longestDistance,
        MemberPreference settings,
        MemberProfile about) {

    /**
     * 소속 크루.
     *
     * @param crewId 크루 ID
     * @param name 크루명
     * @param logoUrl 로고, 없으면 null
     */
    public record Crew(long crewId, String name, String logoUrl) {}
}
