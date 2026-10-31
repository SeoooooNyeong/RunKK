package com.a207.runkk.member;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/** member 시험용 입력 값. */
public final class MemberFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0);

    private MemberFixtures() {}

    public static Map<ConsentItem, Boolean> requiredConsents() {
        Map<ConsentItem, Boolean> consents = new EnumMap<>(ConsentItem.class);
        consents.put(ConsentItem.AGE_14_PLUS, true);
        consents.put(ConsentItem.TERMS, true);
        consents.put(ConsentItem.PRIVACY, true);
        consents.put(ConsentItem.LOCATION, true);
        return consents;
    }

    /** 가입 입력(v0.11 D-90: 닉네임과 동의만). */
    public static SignUpForm form(String nickname) {
        return new SignUpForm(nickname, requiredConsents());
    }

    public static SocialAccount account(String id) {
        return new SocialAccount(ProviderType.KAKAO, id);
    }

    public static Member member(String nickname) {
        return member(nickname, NOW);
    }

    /** 가입 뒤 프로필 편집으로 출생연도 1995·여성·소개를 넣은 회원(v0.10 가입 항목은 프로필 편집으로만 넣는다, D-90). */
    public static Member member(String nickname, LocalDateTime joinedAt) {
        Member m =
                Member.signUp(account(nickname), form(nickname), Nickname.of(nickname), joinedAt);
        m.editProfile(
                new ProfileEdit(null, 1995, Gender.FEMALE, "안녕하세요"), null, joinedAt.getYear());
        return m;
    }
}
