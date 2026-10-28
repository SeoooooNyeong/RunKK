package com.a207.runkk.profile;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.member.DisclosureLevel;

/**
 * 공개 수준별 프로필 항목 거르기(설계 6.1.5 ProfileDisclosure, 6.4 다형성, D-52). 어느 수준인지는 정하지
 * 않는다(member.DisclosureLevel이 정한다). 상수마다 filter가 남길 항목을 정한다.
 */
public enum ProfileDisclosure {
    /** 전부 + 최장 거리 + 설정(FR-005). */
    SELF {
        @Override
        public ProfileView filter(ProfileData data) {
            return view(this, data, true, true);
        }
    },
    /** 설정·최장 거리를 뺀 항목. */
    PUBLIC {
        @Override
        public ProfileView filter(ProfileData data) {
            return view(this, data, false, false);
        }
    },
    /** 표시명, 아바타 얼굴·배경, 티어, 소속 크루(Q-69, FR-202). */
    PRIVATE {
        @Override
        public ProfileView filter(ProfileData data) {
            return new ProfileView(
                    data.memberId(),
                    this,
                    data.displayName(),
                    data.faceKey(),
                    data.avatarBackgroundKey(),
                    data.tier(),
                    null,
                    data.crew(),
                    null,
                    null,
                    null);
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
                    null);
        }
    };

    /** member.DisclosureLevel과 같은 이름의 상수. */
    public static ProfileDisclosure from(DisclosureLevel level) {
        return valueOf(level.name());
    }

    /** 이 수준이 보이는 항목만 남긴 프로필. */
    public abstract ProfileView filter(ProfileData data);

    private static ProfileView view(
            ProfileDisclosure level, ProfileData data, boolean longest, boolean settings) {
        return new ProfileView(
                data.memberId(),
                level,
                data.displayName(),
                data.faceKey(),
                data.avatarBackgroundKey(),
                data.tier(),
                data.runimalType(),
                data.crew(),
                longest ? data.longestDistance() : null,
                settings ? data.settings() : null,
                data.about());
    }
}
