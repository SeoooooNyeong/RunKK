package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.requiredConsents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * v0.11 member 단위(test.md 2.1.10.18 TC-6172~TC-6188, 2.1.10.23 TC-6217·TC-6219·TC-6220): 게스트·연동·마지막
 * 접속, 게스트 자격 증명, 기본 닉네임, 필수 동의 검사, 테마·근접 알림 설정.
 */
@DisplayName("v0.11 회원: 게스트·연동·마지막 접속·자격 증명·닉네임·동의·설정")
class MemberGuestV011Test {

    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 19, 0);

    private static Member guest(String nickname, LocalDateTime now) {
        Map<ConsentItem, Boolean> consents = requiredConsents();
        consents.put(ConsentItem.MARKETING, false);
        consents.put(ConsentItem.LLM_TRANSFER, false);
        return Member.guest(Nickname.of(nickname), consents, now);
    }

    private static Member linked(String nickname) {
        return Member.signUp(
                new SocialAccount(ProviderType.GOOGLE, "g-" + nickname),
                new SignUpForm(null, requiredConsents()),
                Nickname.of(nickname),
                N);
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    @Test
    @DisplayName("TC-6172 Member.guest: 계정 없음·GUEST·해시 없음·기본값")
    void guest_defaults() {
        Member g = guest("러너0042", N);

        assertThat(g.getAccountType()).isEqualTo(AccountType.GUEST);
        assertThat(g.isGuest()).isTrue();
        assertThat(g.account()).isNull();
        assertThat(g.guestCredentialHash()).isNull();
        assertThat(g.lastAccessAt()).isEqualTo(N);
        assertThat(g.getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(g.getLevel()).isEqualTo(1);
        assertThat(g.getSettings().recordVisibility()).isEqualTo(RecordVisibility.PUBLIC);
        assertThat(g.getSettings().theme()).isEqualTo(ScreenTheme.SYSTEM);
        assertThat(g.getSettings().accountType()).isEqualTo(AccountType.GUEST);
        assertThat(g.getSettings().linkedProvider()).isNull();
        assertThat(g.getSettings().battleSettings().proximityAlert()).isTrue();
        assertThat(g.consentRecords()).hasSize(6);
    }

    @Test
    @DisplayName("TC-6173 attachGuestCredential: 한 번만, 게스트만")
    void attachGuestCredential() {
        Member g = guest("러너0042", N);
        String h1 = GuestCredential.hashOf("s-1");
        g.attachGuestCredential(h1);
        assertThat(g.guestCredentialHash()).isEqualTo(h1);

        assertThatThrownBy(() -> g.attachGuestCredential(GuestCredential.hashOf("s-2")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(g.guestCredentialHash()).isEqualTo(h1);
        assertThatThrownBy(() -> linked("연동곰").attachGuestCredential(h1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-6174 linkSocialAccount: LINKED로 한 방향, 해시 지움, 두 번째는 ALREADY_LINKED")
    void linkSocialAccount() {
        Member g = guest("러너0042", N);
        g.attachGuestCredential(GuestCredential.hashOf("s-1"));

        g.linkSocialAccount(new SocialAccount(ProviderType.KAKAO, "k-77"));
        assertThat(g.getAccountType()).isEqualTo(AccountType.LINKED);
        assertThat(g.account()).isEqualTo(new SocialAccount(ProviderType.KAKAO, "k-77"));
        assertThat(g.guestCredentialHash()).isNull();
        assertThat(g.getSettings().linkedProvider()).isEqualTo(ProviderType.KAKAO);

        assertThatThrownBy(() -> g.linkSocialAccount(new SocialAccount(ProviderType.GOOGLE, "g-1")))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.ALREADY_LINKED));
        assertThat(g.account()).isEqualTo(new SocialAccount(ProviderType.KAKAO, "k-77"));

        Member withdrawn = guest("러너0043", N);
        withdrawn.withdraw(N);
        assertThatThrownBy(
                        () ->
                                withdrawn.linkSocialAccount(
                                        new SocialAccount(ProviderType.KAKAO, "k-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-6175 matchesGuestSecret: 게스트·같은 해시만 참")
    void matchesGuestSecret() {
        Member g = guest("러너0042", N);
        g.attachGuestCredential(GuestCredential.hashOf("s-1"));
        assertThat(g.matchesGuestSecret("s-1")).isTrue();
        assertThat(g.matchesGuestSecret("s-2")).isFalse();
        assertThat(g.matchesGuestSecret("")).isFalse();
        assertThat(g.matchesGuestSecret(null)).isFalse();
        assertThat(linked("연동곰").matchesGuestSecret("s-1")).isFalse();
        assertThat(guest("러너0044", N).matchesGuestSecret("s-1")).isFalse();
    }

    @Test
    @DisplayName("TC-6176·TC-6177·TC-6178 recordAccess: 날짜가 바뀐 때만, 되돌리지 않음, ACTIVE만")
    void recordAccess() {
        LocalDateTime at8 = LocalDateTime.of(2026, 10, 22, 8, 0);
        Member g = guest("러너0042", at8);

        assertThat(g.recordAccess(LocalDateTime.of(2026, 10, 22, 23, 59, 59, 999_000_000)))
                .isFalse();
        assertThat(g.lastAccessAt()).isEqualTo(at8);
        assertThat(g.recordAccess(LocalDateTime.of(2026, 10, 23, 0, 0))).isTrue();
        assertThat(g.lastAccessAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 0, 0));
        assertThat(g.recordAccess(LocalDateTime.of(2026, 10, 23, 0, 0, 1))).isFalse();

        Member late = guest("러너0043", at8);
        assertThat(late.recordAccess(LocalDateTime.of(2026, 10, 21, 23, 0))).isFalse();
        assertThat(late.lastAccessAt()).isEqualTo(at8);

        late.withdraw(N);
        assertThatThrownBy(() -> late.recordAccess(N)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-6179 게스트 탈퇴는 해시를 지우고, 가입은 LINKED·lastAccessAt·PUBLIC")
    void withdraw_andSignUp() {
        Member g = guest("러너0042", N);
        g.attachGuestCredential(GuestCredential.hashOf("s"));
        g.withdraw(N);
        assertThat(g.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(g.nickname()).isNull();
        assertThat(g.guestCredentialHash()).isNull();

        Member m =
                Member.signUp(
                        new SocialAccount(ProviderType.KAKAO, "k-1"),
                        new SignUpForm(null, requiredConsents()),
                        Nickname.of("러너0001"),
                        N);
        assertThat(m.getAccountType()).isEqualTo(AccountType.LINKED);
        assertThat(m.lastAccessAt()).isEqualTo(N);
        assertThat(m.getSettings().recordVisibility()).isEqualTo(RecordVisibility.PUBLIC);
        assertThat(m.getSettings().linkedProvider()).isEqualTo(ProviderType.KAKAO);
    }

    /** 고정 바이트·정수 SecureRandom 대역. */
    static final class FixedRandom extends SecureRandom {
        private final int fill;

        FixedRandom(int fill) {
            this.fill = fill;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            java.util.Arrays.fill(bytes, (byte) fill);
        }
    }

    @Test
    @DisplayName("TC-6180 GuestCredential.issue: g1.<id>.<43자>, 해시는 hashOf(비밀)")
    void issue() {
        GuestCredential.Issued issued = GuestCredential.issue(42, new FixedRandom(0));
        assertThat(issued.token()).isEqualTo("g1.42." + "A".repeat(43));
        assertThat(issued.hash()).isEqualTo(GuestCredential.hashOf("A".repeat(43)));
        assertThat(issued.toString()).doesNotContain("A".repeat(43));

        GuestCredential.Issued a = GuestCredential.issue(42, new SecureRandom());
        GuestCredential.Issued b = GuestCredential.issue(42, new SecureRandom());
        assertThat(a.token()).isNotEqualTo(b.token()).matches("g1\\.42\\.[A-Za-z0-9_-]{43}");
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    @DisplayName("TC-6181 GuestCredential.parse: 형식 위반은 모두 비어 있음")
    void parse() {
        String secret = "A".repeat(42) + "w";
        assertThat(GuestCredential.parse("g1.42." + secret))
                .contains(new GuestCredential.Parsed(42, secret));
        assertThat(GuestCredential.parse("g2.42." + secret)).isEmpty();
        assertThat(GuestCredential.parse("g1.abc." + secret)).isEmpty();
        assertThat(GuestCredential.parse("g1.42." + "A".repeat(42))).isEmpty();
        assertThat(GuestCredential.parse("g1.42." + "A".repeat(42) + "+")).isEmpty();
        assertThat(GuestCredential.parse("g1.42." + secret + "=")).isEmpty();
        assertThat(GuestCredential.parse("g1.42")).isEmpty();
        assertThat(GuestCredential.parse("")).isEmpty();
        assertThat(GuestCredential.parse(null)).isEmpty();
        assertThat(GuestCredential.parse("g1.0." + secret)).isEmpty();
        assertThat(GuestCredential.parse("g1.-1." + secret)).isEmpty();
        assertThat(GuestCredential.parse("g1.99999999999999999999." + secret)).isEmpty();
    }

    @Test
    @DisplayName("TC-6182 GuestCredential.hashOf: SHA-256 16진 64자")
    void hashOf() {
        assertThat(GuestCredential.hashOf("abc"))
                .isEqualToIgnoringCase(
                        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("TC-6183 Nickname.numbered: 0~99999, 4자리 미만은 0으로 채움")
    void numbered() {
        assertThatThrownBy(() -> Nickname.numbered(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Nickname.numbered(0).value()).isEqualTo("러너0000");
        assertThat(Nickname.numbered(7).value()).isEqualTo("러너0007");
        assertThat(Nickname.numbered(9_999).value()).isEqualTo("러너9999");
        assertThat(Nickname.numbered(10_000).value()).isEqualTo("러너10000");
        assertThat(Nickname.numbered(99_999).value()).isEqualTo("러너99999");
        assertThatThrownBy(() -> Nickname.numbered(100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<ConsentItem, Boolean> consents(
            Boolean terms, Boolean privacy, Boolean location, Boolean age) {
        Map<ConsentItem, Boolean> map = new EnumMap<>(ConsentItem.class);
        if (terms != null) {
            map.put(ConsentItem.TERMS, terms);
        }
        if (privacy != null) {
            map.put(ConsentItem.PRIVACY, privacy);
        }
        if (location != null) {
            map.put(ConsentItem.LOCATION, location);
        }
        if (age != null) {
            map.put(ConsentItem.AGE_14_PLUS, age);
        }
        return map;
    }

    private static Optional<ReasonCode> signUpReason(Map<ConsentItem, Boolean> c) {
        try {
            new SignUpForm(null, c).validate();
            return Optional.empty();
        } catch (BusinessRuleException e) {
            return Optional.of(e.reason());
        }
    }

    private static Optional<ReasonCode> guestReason(Map<ConsentItem, Boolean> c) {
        try {
            new GuestStartForm(c).validate();
            return Optional.empty();
        } catch (BusinessRuleException e) {
            return Optional.of(e.reason());
        }
    }

    @Test
    @DisplayName("TC-6184 RequiredConsents: 결정표 7열, 두 입력 클래스가 같은 결과")
    void requiredConsents_decisionTable() {
        Object[][] table = {
            {consents(true, true, true, true), null},
            {consents(false, true, true, true), ReasonCode.CONSENT_MISSING},
            {consents(true, null, true, true), ReasonCode.CONSENT_MISSING},
            {consents(true, true, false, true), ReasonCode.CONSENT_MISSING},
            {consents(true, true, true, false), ReasonCode.UNDER_14},
            {consents(true, true, true, null), ReasonCode.UNDER_14},
            {consents(false, true, true, false), ReasonCode.CONSENT_MISSING},
        };
        for (Object[] row : table) {
            @SuppressWarnings("unchecked")
            Map<ConsentItem, Boolean> c = (Map<ConsentItem, Boolean>) row[0];
            Optional<ReasonCode> expected = Optional.ofNullable((ReasonCode) row[1]);
            assertThat(signUpReason(c)).as(c.toString()).isEqualTo(expected);
            assertThat(guestReason(c)).as(c.toString()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("TC-6038 ②③ GuestStartForm: 가입 때 받지 않는 동의 항목은 400")
    void guestStartForm_rejectsLaterConsents() {
        for (ConsentItem later :
                new ConsentItem[] {ConsentItem.HEART_RATE, ConsentItem.FACE_TRANSFER}) {
            Map<ConsentItem, Boolean> c = requiredConsents();
            c.put(later, true);
            assertThatThrownBy(() -> new GuestStartForm(c).validate())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("TC-6185 SignUpForm.validate는 닉네임을 검사하지 않는다")
    void signUpForm_doesNotValidateNickname() {
        for (String nickname : new String[] {"가", null, "러너", "가".repeat(21)}) {
            new SignUpForm(nickname, requiredConsents()).validate();
        }
    }

    @Test
    @DisplayName("TC-6186 THEME: SYSTEM·LIGHT·DARK만 저장, 나머지 SETTING_INVALID")
    void theme() {
        Member m = linked("테마곰");
        for (String value : new String[] {"LIGHT", "DARK", "SYSTEM"}) {
            m.changeSetting(new SettingChange(SettingItem.THEME, value, null));
            assertThat(m.getSettings().theme()).isEqualTo(ScreenTheme.valueOf(value));
        }
        m.changeSetting(new SettingChange(SettingItem.THEME, "DARK", null));
        for (String value : new String[] {"dark", "", "BLUE"}) {
            assertThatThrownBy(
                            () ->
                                    m.changeSetting(
                                            new SettingChange(SettingItem.THEME, value, null)))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SETTING_INVALID));
            assertThat(m.getSettings().theme()).isEqualTo(ScreenTheme.DARK);
        }
    }

    @Test
    @DisplayName("TC-6187 BATTLE_SETTINGS proximityAlert: 기본 참, 빠지면 현재 값 유지")
    void battleSettings_proximityAlert() {
        BattleSettings defaults = BattleSettings.defaults();
        assertThat(defaults.proximityAlert()).isTrue();
        assertThat(defaults.defaultDistance()).isEqualTo(StandardDistance.KM5);
        assertThat(defaults.voice()).isTrue();
        assertThat(defaults.vibration()).isTrue();
        assertThat(defaults.withProximityAlert(false).proximityAlert()).isFalse();

        Member m = linked("근접곰");
        m.changeSetting(
                new SettingChange(
                        SettingItem.BATTLE_SETTINGS,
                        "{\"defaultDistance\":\"KM5\",\"voice\":true,\"vibration\":true,"
                                + "\"gapAlertInterval\":\"KM1\",\"proximityAlert\":false}",
                        null));
        assertThat(m.getSettings().battleSettings().proximityAlert()).isFalse();

        m.changeSetting(
                new SettingChange(
                        SettingItem.BATTLE_SETTINGS,
                        "{\"defaultDistance\":\"KM5\",\"voice\":false,\"vibration\":true,"
                                + "\"gapAlertInterval\":\"KM1\"}",
                        null));
        assertThat(m.getSettings().battleSettings().voice()).isFalse();
        assertThat(m.getSettings().battleSettings().proximityAlert()).isFalse();

        m.changeSetting(
                new SettingChange(
                        SettingItem.BATTLE_SETTINGS,
                        "{\"defaultDistance\":\"KM5\",\"voice\":false,\"vibration\":true,"
                                + "\"gapAlertInterval\":\"KM1\",\"proximityAlert\":true}",
                        null));
        assertThat(m.getSettings().battleSettings().proximityAlert()).isTrue();

        assertThatThrownBy(
                        () ->
                                m.changeSetting(
                                        new SettingChange(
                                                SettingItem.BATTLE_SETTINGS,
                                                "{\"defaultDistance\":\"KM5\",\"voice\":false,"
                                                        + "\"vibration\":true,"
                                                        + "\"gapAlertInterval\":\"KM1\","
                                                        + "\"proximityAlert\":\"yes\"}",
                                                null)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SETTING_INVALID));
        assertThat(m.getSettings().battleSettings().proximityAlert()).isTrue();
    }

    @Test
    @DisplayName("TC-6217 Nickname.fromProviderName: 2자 미만 비움, 20자 초과 앞 20자(코드 포인트)")
    void fromProviderName() {
        assertThat(Nickname.fromProviderName("")).isEmpty();
        assertThat(Nickname.fromProviderName(null)).isEmpty();
        assertThat(Nickname.fromProviderName("   ")).isEmpty();
        assertThat(Nickname.fromProviderName(" 가 ")).isEmpty();
        assertThat(Nickname.fromProviderName("가나")).contains(Nickname.of("가나"));
        assertThat(Nickname.fromProviderName("가".repeat(20))).contains(Nickname.of("가".repeat(20)));
        assertThat(Nickname.fromProviderName("가".repeat(21)).orElseThrow().value())
                .isEqualTo("가".repeat(20));
        assertThat(Nickname.fromProviderName(" 러너가나다 ").orElseThrow().value()).isEqualTo("러너가나다");
        String runner = "🏃";
        assertThat(Nickname.fromProviderName(runner.repeat(10) + "가").orElseThrow().value())
                .isEqualTo(runner.repeat(10) + "가");
        assertThat(Nickname.fromProviderName(runner.repeat(21)).orElseThrow().value())
                .isEqualTo(runner.repeat(20));
    }

    @Test
    @DisplayName("TC-6219 Nickname.of·withSuffix: 코드 포인트로 세고 자른다")
    void nickname_codePoints() {
        String runner = "🏃";
        assertThatThrownBy(() -> Nickname.of(runner))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NICKNAME_INVALID));
        assertThat(Nickname.of(runner.repeat(2)).value()).isEqualTo(runner.repeat(2));
        assertThat(Nickname.of(runner.repeat(20)).value()).isEqualTo(runner.repeat(20));
        assertThatThrownBy(() -> Nickname.of(runner.repeat(21)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NICKNAME_INVALID));
        assertThat(Nickname.of(runner.repeat(20)).withSuffix(9999).value())
                .isEqualTo(runner.repeat(16) + "9999");
    }

    @Test
    @DisplayName("TC-6220 editProfile 출생연도: 만 14세 이하 UNDER_14, 4자리 아님 400")
    void editProfile_birthYear() {
        Member m = linked("나이곰");
        m.editProfile(new ProfileEdit(null, 2011, null, null, null), null, 2026);
        assertThat(m.ageIn(2026)).hasValue(15);
        for (int year : new int[] {2012, 2013}) {
            assertThatThrownBy(
                            () ->
                                    m.editProfile(
                                            new ProfileEdit(null, year, null, null, null),
                                            null,
                                            2026))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.UNDER_14));
        }
        for (int year : new int[] {201, 20111}) {
            assertThatThrownBy(
                            () ->
                                    m.editProfile(
                                            new ProfileEdit(null, year, null, null, null),
                                            null,
                                            2026))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(m.ageIn(2026)).hasValue(15);
    }
}
