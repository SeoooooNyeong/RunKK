package com.a207.runkk.member;

/**
 * 열람자에게 대상 회원의 프로필을 어디까지 보이는가(설계 6.1.2, D-52). 공개 범위 규칙은 이 enum 한 곳에만 있다. 수준마다 화면에 남길 항목은
 * profile.ProfileDisclosure가 정한다.
 */
public enum DisclosureLevel {
    SELF(true),
    WITHDRAWN(false),
    PUBLIC(true);

    private final boolean showsProfile;

    DisclosureLevel(boolean showsProfile) {
        this.showsProfile = showsProfile;
    }

    /**
     * 본인 → 탈퇴 → 공개 순으로 처음 맞는 수준. 다른 러너에게 닉네임·아바타·레벨·티어·Runimal·누적 거리·최장 거리·러닝 횟수·소개는 늘 보이고, 나이대·성별만
     * 항목별 공개 값으로 감춘다(FR-006, v0.14 D-241. PRIVATE 수준은 지웠다).
     */
    public static DisclosureLevel of(long viewerId, long targetId, MemberStatus status) {
        if (viewerId == targetId) {
            return SELF;
        }
        if (status == MemberStatus.WITHDRAWN) {
            return WITHDRAWN;
        }
        return PUBLIC;
    }

    /** 크루원 목록 등에서 프로필(Runimal 유형 등)을 보일지. */
    public boolean showsProfile() {
        return showsProfile;
    }
}
