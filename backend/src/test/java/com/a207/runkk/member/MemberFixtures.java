package com.a207.runkk.member;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.RecordVisibility;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

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

    public static SignUpForm form(String nickname) {
        return new SignUpForm(
                nickname,
                1995,
                Gender.FEMALE,
                null,
                "안녕하세요",
                requiredConsents(),
                RecordVisibility.PUBLIC,
                Set.of(PersonalityTag.SOLO_RUN, PersonalityTag.MORNING));
    }

    public static SocialAccount account(String id) {
        return new SocialAccount(ProviderType.KAKAO, id);
    }

    public static Member member(String nickname) {
        return member(nickname, NOW);
    }

    public static Member member(String nickname, LocalDateTime joinedAt) {
        return Member.signUp(account(nickname), form(nickname), Nickname.of(nickname), joinedAt);
    }
}
