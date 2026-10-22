package com.a207.runkk.member;

/**
 * 열람자에게 대상 회원의 프로필을 어디까지 보이는가(설계 6.1.2, D-52). 공개 범위 규칙은 이 enum 한 곳에만 있다. 수준마다 화면에 남길 항목은
 * profile.ProfileDisclosure가 정한다.
 */
public enum DisclosureLevel {
    SELF(true),
    WITHDRAWN(false),
    PUBLIC(true),
    PRIVATE(false);

    private final boolean showsProfile;

    DisclosureLevel(boolean showsProfile) {
        this.showsProfile = showsProfile;
    }

    /** 본인 → 탈퇴 → 공개 → 비공개 순으로 처음 맞는 수준. */
    public static DisclosureLevel of(
            long viewerId, long targetId, MemberStatus status, ProfileVisibility visibility) {
        if (viewerId == targetId) {
            return SELF;
        }
        if (status == MemberStatus.WITHDRAWN) {
            return WITHDRAWN;
        }
        return visibility == ProfileVisibility.PUBLIC ? PUBLIC : PRIVATE;
    }

    /** 크루원 목록 등에서 프로필(Runimal 유형 등)을 보일지. */
    public boolean showsProfile() {
        return showsProfile;
    }
}
