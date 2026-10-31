package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** member 값 객체·열거형·입력(6.1.2). */
@org.junit.jupiter.api.DisplayName("member 값 객체·열거형·입력")
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
    void settingItem_hasFourteenConstantsAndConsentMapping() {
        assertThat(SettingItem.values()).hasSize(14); // 자료사전 13개 + v0.11 THEME
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
        // 규칙 1: 필수 동의 누락
        Map<ConsentItem, Boolean> missing = new EnumMap<>(MemberFixtures.requiredConsents());
        missing.put(ConsentItem.LOCATION, false);
        assertReason(() -> new SignUpForm("가", missing).validate(), ReasonCode.CONSENT_MISSING);

        // 규칙 2: 만 14세 확인 미동의(출생연도는 가입 항목이 아니다, D-90)
        Map<ConsentItem, Boolean> noAge = new EnumMap<>(MemberFixtures.requiredConsents());
        noAge.remove(ConsentItem.AGE_14_PLUS);
        assertReason(() -> new SignUpForm("달리는곰", noAge).validate(), ReasonCode.UNDER_14);

        // 규칙 3: 통과(닉네임은 검사하지 않는다, D-143)
        new SignUpForm("가", MemberFixtures.requiredConsents()).validate();
        new SignUpForm(null, MemberFixtures.requiredConsents()).validate();
    }

    @Test
    void disclosureLevel_threeRows() {
        // v0.14 D-241: PRIVATE 수준을 지웠다. 본인 → 탈퇴 → 공개
        assertThat(DisclosureLevel.of(1, 1, MemberStatus.ACTIVE)).isEqualTo(DisclosureLevel.SELF);
        assertThat(DisclosureLevel.of(1, 2, MemberStatus.WITHDRAWN))
                .isEqualTo(DisclosureLevel.WITHDRAWN);
        assertThat(DisclosureLevel.of(1, 2, MemberStatus.ACTIVE)).isEqualTo(DisclosureLevel.PUBLIC);
        assertThat(DisclosureLevel.values())
                .containsExactly(
                        DisclosureLevel.SELF, DisclosureLevel.WITHDRAWN, DisclosureLevel.PUBLIC);
        assertThat(DisclosureLevel.SELF.showsProfile()).isTrue();
        assertThat(DisclosureLevel.PUBLIC.showsProfile()).isTrue();
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

    private static void assertReason(Runnable action, ReasonCode reason) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(reason);
    }
}
