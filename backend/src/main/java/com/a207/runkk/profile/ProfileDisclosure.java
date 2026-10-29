package com.a207.runkk.profile;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberProfile;
import com.a207.runkk.member.ProfileVisibility;
import java.util.List;

/**
 * 공개 수준별 프로필 항목 거르기(설계 6.1.5 ProfileDisclosure, 6.4 다형성, D-52). 어느 수준인지는 정하지
 * 않는다(member.DisclosureLevel이 정한다). 상수마다 filter가 남길 항목을 정한다.
 */
public enum ProfileDisclosure {
    /** 전부 + 최장 거리 + 설정(FR-005). */
    SELF {
        @Override
        public ProfileView filter(ProfileData data) {
            return view(this, data, data.about(), true, List.of());
        }
    },
    /**
     * 다른 러너(v0.14 D-241·D-243): 닉네임·아바타·레벨·티어·Runimal·누적 거리·최장 거리·러닝 횟수·소개는 늘 보이고, 나이대·성별은 각 공개 값이
     * PRIVATE면 감추며 공개 값 두 개는 싣지 않는다. 최근 러닝은 ProfileQueryService가 기록 공개 범위로 거른 값이다. 설정은 싣지 않는다.
     */
    PUBLIC {
        @Override
        public ProfileView filter(ProfileData data) {
            return view(this, data, publicAbout(data.about()), false, data.recentRuns());
        }
    },
    /** 표시명 "탈퇴한 사용자"만(FR-006). */
    WITHDRAWN {
        @Override
        public ProfileView filter(ProfileData data) {
            return new ProfileView(
                    data.memberId(),
                    this,
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
    };

    /** member.DisclosureLevel과 같은 이름의 상수. */
    public static ProfileDisclosure from(DisclosureLevel level) {
        return valueOf(level.name());
    }

    /** 이 수준이 보이는 항목만 남긴 프로필. */
    public abstract ProfileView filter(ProfileData data);

    private static ProfileView view(
            ProfileDisclosure level,
            ProfileData data,
            MemberProfile about,
            boolean settings,
            List<RecentRun> recentRuns) {
        return new ProfileView(
                data.memberId(),
                level,
                data.displayName(),
                data.faceKey(),
                data.avatarBackgroundKey(),
                data.tier(),
                data.runimalType(),
                data.crew(),
                data.longestDistance(),
                settings ? data.settings() : null,
                about,
                data.totalDistance(),
                data.runCount(),
                recentRuns);
    }

    /** 나이대·성별을 항목 공개 값으로 감추고 공개 값 두 개는 뺀다(v0.14 D-241). 소개는 늘 보인다. */
    private static MemberProfile publicAbout(MemberProfile about) {
        if (about == null) {
            return null;
        }
        return new MemberProfile(
                about.level(),
                about.intro(),
                about.ageBandVisibility() == ProfileVisibility.PUBLIC ? about.ageBand() : null,
                about.genderVisibility() == ProfileVisibility.PUBLIC ? about.gender() : null,
                null,
                null);
    }
}
