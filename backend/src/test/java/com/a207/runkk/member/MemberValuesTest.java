package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** member 값 객체·열거형·입력(6.1.2). */
class MemberValuesTest {

    @Test
    void nickname_lengthRuleAndTrim() {
        assertThat(Nickname.of("  달리는곰 ").value()).isEqualTo("달리는곰");
        assertThatThrownBy(() -> Nickname.of("가"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.NICKNAME_INVALID);
        assertThatThrownBy(() -> Nickname.of("가".repeat(21)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Nickname.of(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nickname_withSuffix_cutsFrontPartToTwentyChars() {
        assertThat(Nickname.of("달리는곰").withSuffix(7).value()).isEqualTo("달리는곰0007");
        assertThat(Nickname.of("가".repeat(20)).withSuffix(1234).value())
                .isEqualTo("가".repeat(16) + "1234");
        assertThatThrownBy(() -> Nickname.of("달리는곰").withSuffix(10000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void socialAccount_requiresAccountId() {
        assertThatThrownBy(() -> new SocialAccount(ProviderType.GOOGLE, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SocialAccount(ProviderType.GOOGLE, "1"))
                .isEqualTo(new SocialAccount(ProviderType.GOOGLE, "1"));
    }

    @Test
    void consentItem_requiredAndWithdrawable() {
        assertThat(ConsentItem.AGE_14_PLUS.isRequired()).isTrue();
        assertThat(ConsentItem.LOCATION.isRequired()).isTrue();
        assertThat(ConsentItem.MARKETING.isRequired()).isFalse();
        assertThat(ConsentItem.HEART_RATE.collectedAtSignUp()).isFalse();
        assertThat(ConsentItem.LLM_TRANSFER.collectedAtSignUp()).isTrue();
        assertThat(ConsentItem.LOCATION.isWithdrawable()).isTrue();
        assertThat(ConsentItem.TERMS.isWithdrawable()).isFalse();
        assertThat(ConsentItem.PRIVACY.isWithdrawable()).isFalse();
        assertThat(ConsentItem.FACE_TRANSFER.isWithdrawable()).isTrue();
    }

    @Test
    void settingItem_hasThirteenConstantsAndConsentMapping() {
        assertThat(SettingItem.values()).hasSize(13);
        assertThat(SettingItem.LOCATION_CONSENT.consentItem()).contains(ConsentItem.LOCATION);
        assertThat(SettingItem.HEART_RATE_CONSENT.consentItem()).contains(ConsentItem.HEART_RATE);
        assertThat(SettingItem.UNIT.consentItem()).isEmpty();
        SettingItem.UNIT.validate("KM");
        assertThatThrownBy(() -> SettingItem.UNIT.validate("LY"))
                .isInstanceOf(BusinessRuleException.class);
        SettingItem.RECORD_VISIBILITY.validate("CREW");
        SettingItem.WATCH_LINK.validate("false");
        assertThatThrownBy(() -> SettingItem.WATCH_LINK.validate("yes"))
                .isInstanceOf(BusinessRuleException.class);
        SettingItem.RUN_SETTINGS.validate(
                "{\"target\":\"KM5\",\"voice\":true,\"vibration\":false,\"followCoachPlan\":true}");
        assertThatThrownBy(() -> SettingItem.RUN_SETTINGS.validate("{\"target\":\"KM7\"}"))
                .isInstanceOf(BusinessRuleException.class);
        SettingItem.BATTLE_SETTINGS.validate(
                "{\"defaultDistance\":\"KM3\",\"voice\":true,\"vibration\":true,\"gapAlertInterval\":\"M500\"}");
        assertThatThrownBy(
                        () ->
                                SettingItem.BATTLE_SETTINGS.validate(
                                        "{\"defaultDistance\":\"HALF\",\"voice\":true,\"vibration\":true,\"gapAlertInterval\":\"M500\"}"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void settingChange_consentDetection() {
        assertThat(new SettingChange(SettingItem.MARKETING_CONSENT, "true", null).isConsentChange())
                .isTrue();
        assertThat(new SettingChange(SettingItem.UNIT, "KM", null).isConsentChange()).isFalse();
        assertThatThrownBy(() -> new SettingChange(SettingItem.ALERT, "true", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memberSettings_alertNeedsKnownNotificationType() {
        MemberSettings settings = MemberSettings.defaults(RecordVisibility.PUBLIC);
        assertThatThrownBy(
                        () ->
                                settings.apply(
                                        new SettingChange(
                                                SettingItem.ALERT, "false", "NOT_A_TYPE")))
                .isInstanceOf(BusinessRuleException.class);
        settings.apply(new SettingChange(SettingItem.ALERT, "false", "KICKED"));
        assertThat(settings.allowsPush("KICKED")).isFalse();
        settings.apply(new SettingChange(SettingItem.ALERT, "true", "KICKED"));
        assertThat(settings.allowsPush("KICKED")).isTrue();
    }

    @Test
    void signUpForm_decisionTableOrder() {
        int year = 2026;
        // 규칙 1: 필수 동의 누락
        Map<ConsentItem, Boolean> missing = new EnumMap<>(MemberFixtures.requiredConsents());
        missing.put(ConsentItem.LOCATION, false);
        SignUpForm rule1 = withConsents(missing, 2020, "가");
        assertThat(rule1.missingRequiredConsents()).containsExactly(ConsentItem.LOCATION);
        assertReason(() -> rule1.validate(year), ReasonCode.CONSENT_MISSING);

        // 규칙 2: 만 14세 미만(확인 미동의 또는 출생연도)
        Map<ConsentItem, Boolean> noAge = new EnumMap<>(MemberFixtures.requiredConsents());
        noAge.remove(ConsentItem.AGE_14_PLUS);
        assertThat(withConsents(noAge, null, "달리는곰").isUnder14(year)).isTrue();
        SignUpForm rule2 = withConsents(MemberFixtures.requiredConsents(), 2013, "가");
        assertThat(rule2.isUnder14(year)).isTrue();
        assertReason(() -> rule2.validate(year), ReasonCode.UNDER_14);
        assertThat(withConsents(MemberFixtures.requiredConsents(), 2012, "달리는곰").isUnder14(year))
                .isFalse();

        // 규칙 3: 닉네임 오류
        SignUpForm rule3 = withConsents(MemberFixtures.requiredConsents(), 1990, "가");
        assertReason(() -> rule3.validate(year), ReasonCode.NICKNAME_INVALID);

        // 규칙 4: 통과 (닉네임 없음도 통과)
        withConsents(MemberFixtures.requiredConsents(), 1990, null).validate(year);
    }

    @Test
    void signUpForm_tagRule() {
        SignUpForm f =
                new SignUpForm(
                        "달리는곰",
                        null,
                        null,
                        null,
                        null,
                        MemberFixtures.requiredConsents(),
                        RecordVisibility.PUBLIC,
                        Set.of(
                                com.a207.runkk.common.PersonalityTag.SOLO_RUN,
                                com.a207.runkk.common.PersonalityTag.SOCIAL_RUN));
        assertReason(() -> f.validate(2026), ReasonCode.TAG_INVALID);
    }

    @Test
    void disclosureLevel_fourRows() {
        assertThat(DisclosureLevel.of(1, 1, MemberStatus.ACTIVE, ProfileVisibility.PRIVATE))
                .isEqualTo(DisclosureLevel.SELF);
        assertThat(DisclosureLevel.of(1, 2, MemberStatus.WITHDRAWN, ProfileVisibility.PUBLIC))
                .isEqualTo(DisclosureLevel.WITHDRAWN);
        assertThat(DisclosureLevel.of(1, 2, MemberStatus.ACTIVE, ProfileVisibility.PUBLIC))
                .isEqualTo(DisclosureLevel.PUBLIC);
        assertThat(DisclosureLevel.of(1, 2, MemberStatus.ACTIVE, ProfileVisibility.PRIVATE))
                .isEqualTo(DisclosureLevel.PRIVATE);
        assertThat(DisclosureLevel.SELF.showsProfile()).isTrue();
        assertThat(DisclosureLevel.PUBLIC.showsProfile()).isTrue();
        assertThat(DisclosureLevel.PRIVATE.showsProfile()).isFalse();
        assertThat(DisclosureLevel.WITHDRAWN.showsProfile()).isFalse();
    }

    @Test
    void consentHistory_appendsAndWipes() {
        ConsentHistory history = new ConsentHistory();
        assertThat(history.isAgreed(ConsentItem.MARKETING)).isFalse();
        history.record(ConsentItem.MARKETING, true, NOW);
        history.record(ConsentItem.MARKETING, false, NOW.plusMinutes(1));
        assertThat(history.isAgreed(ConsentItem.MARKETING)).isFalse();
        assertThat(history.records()).hasSize(2);
        assertThatThrownBy(() -> history.records().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        history.wipe();
        assertThat(history.records()).isEmpty();
    }

    @Test
    void runAndBattleSettings_defaults() {
        assertThat(RunSettings.defaults())
                .isEqualTo(new RunSettings(FreeRunTarget.FREE, true, true, false));
        assertThat(BattleSettings.defaults().defaultDistance())
                .isEqualTo(com.a207.runkk.common.StandardDistance.KM5);
        assertThat(BattleSettings.defaults().gapAlertInterval()).isEqualTo(GapAlertInterval.KM1);
    }

    private static SignUpForm withConsents(
            Map<ConsentItem, Boolean> consents, Integer birthYear, String nickname) {
        return new SignUpForm(
                nickname, birthYear, null, null, null, consents, RecordVisibility.PUBLIC, Set.of());
    }

    private static void assertReason(Runnable action, ReasonCode reason) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(reason);
    }
}
