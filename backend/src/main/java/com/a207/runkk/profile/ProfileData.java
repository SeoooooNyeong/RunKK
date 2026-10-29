package com.a207.runkk.profile;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberProfile;
import java.util.List;

/**
 * 거르기 전 프로필 항목 전부(설계 6.1.5 ProfileDisclosure.filter의 입력). ProfileQueryService가 다른 패키지 조회 결과로 모은다.
 *
 * @param memberId 회원 ID
 * @param displayName 표시명
 * @param faceKey 얼굴 키, 없으면 null
 * @param avatarBackgroundKey 아바타 랜드마크 배경 키, 기본 배경이면 null(FR-202)
 * @param tier 티어
 * @param runimalType 유형 이름, 없으면 null
 * @param crew 소속 크루, 없으면 null
 * @param longestDistance 최장 거리, 없으면 null
 * @param settings 설정
 * @param about 레벨·사진·소개·나이대·성별, 탈퇴면 null
 */
public record ProfileData(
        long memberId,
        DisplayName displayName,
        String faceKey,
        String avatarBackgroundKey,
        Tier tier,
        String runimalType,
        ProfileView.Crew crew,
        Distance longestDistance,
        MemberPreference settings,
        MemberProfile about,
        Distance totalDistance,
        int runCount,
        List<RecentRun> recentRuns) {

    public ProfileData {
        recentRuns = recentRuns == null ? List.of() : List.copyOf(recentRuns);
    }

    /** 탈퇴 회원(다른 항목을 모으지 않는다). */
    static ProfileData withdrawn(long memberId) {
        return new ProfileData(
                memberId,
                DisplayName.WITHDRAWN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                List.of());
    }
}
