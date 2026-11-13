package com.a207.runkk.crew;

import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberProfile;
import com.a207.runkk.member.ProfileVisibility;

/**
 * 크루원 목록·가입 신청 목록에 싣는 회원 보조 정보(피그마 맞춤 W7 F-22·F-22b·F-25·F-29b, contract-W7.md). 공개 규칙은 다른 러너
 * 프로필(profile.ProfileDisclosure.PUBLIC, v0.14 D-241)과 같다: 레벨은 늘 보이고 나이대·성별만 항목별 공개 값으로 감춘다. 본인은 모두와
 * 공개 설정 두 값까지 본다(F-23 토글 초깃값).
 *
 * @param level 레벨, 회원 정보가 없으면 null
 * @param ageBand 나이대("20대"), 감췄거나 없으면 null
 * @param gender 성별, 감췄거나 없으면 null
 * @param ageBandVisibility 나이대 공개 값, 본인만
 * @param genderVisibility 성별 공개 값, 본인만
 */
public record CrewMemberProfile(
        Integer level,
        String ageBand,
        Gender gender,
        ProfileVisibility ageBandVisibility,
        ProfileVisibility genderVisibility) {

    private static final CrewMemberProfile NONE =
            new CrewMemberProfile(null, null, null, null, null);

    /** 열람자에게 보일 값. profile이 null(회원 정보 없음)이면 모두 null. */
    public static CrewMemberProfile shownTo(MemberProfile profile, boolean self) {
        if (profile == null) {
            return NONE;
        }
        if (self) {
            return new CrewMemberProfile(
                    profile.level(),
                    profile.ageBand(),
                    profile.gender(),
                    profile.ageBandVisibility(),
                    profile.genderVisibility());
        }
        return new CrewMemberProfile(
                profile.level(),
                profile.ageBandVisibility() == ProfileVisibility.PUBLIC ? profile.ageBand() : null,
                profile.genderVisibility() == ProfileVisibility.PUBLIC ? profile.gender() : null,
                null,
                null);
    }
}
