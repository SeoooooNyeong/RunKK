package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static com.a207.runkk.member.MemberFixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Member 상태도(2.4.9)의 전이·금지 전이와 티어 순번(14장 단위 시험). */
@org.junit.jupiter.api.DisplayName("회원 엔티티 상태 전이와 티어 순번")
class MemberTest {

    private static final TierBoundary BOUNDARY = new TierBoundary(List.of(30.0, 35.0, 40.0, 45.0));

    @Test
    void signUp_setsDefaults() {
        Member m = member("달리는곰");

        assertThat(m.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(m.getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(m.getLevel()).isEqualTo(1);
        assertThat(m.displayName()).isEqualTo(DisplayName.of("달리는곰"));
        assertThat(m.getTags().tags())
                .containsExactlyInAnyOrder(PersonalityTag.SOLO_RUN, PersonalityTag.MORNING);
        MemberSettingsView s = m.getSettings();
        assertThat(s.unit()).isEqualTo(DistanceUnit.KM);
        assertThat(s.recordVisibility()).isEqualTo(RecordVisibility.PUBLIC);
        assertThat(s.courseMapShared()).isFalse();
        assertThat(s.profileVisibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThat(s.watchLinked()).isFalse();
        assertThat(s.notificationPrefs())
                .hasSize(12)
                .containsValue(true)
                .doesNotContainValue(false);
        assertThat(s.runSettings()).isEqualTo(RunSettings.defaults());
        assertThat(s.battleSettings()).isEqualTo(BattleSettings.defaults());
        assertThat(s.consents())
                .containsEntry(ConsentItem.LOCATION, true)
                .containsEntry(ConsentItem.MARKETING, false)
                .containsEntry(ConsentItem.HEART_RATE, false);
        assertThat(s.birthYear()).isEqualTo(1995);
        assertThat(m.hasConsent(ConsentItem.TERMS)).isTrue();
    }

    @Test
    void signUp_introOver40_isRejected() {
        SignUpForm form = MemberFixtures.form("달리는곰");
        SignUpForm longIntro =
                new SignUpForm(
                        form.nickname(),
                        form.birthYear(),
                        form.gender(),
                        null,
                        "가".repeat(41),
                        form.consents(),
                        form.recordVisibility(),
                        form.tags());
        assertThatThrownBy(
                        () ->
                                Member.signUp(
                                        MemberFixtures.account("1"),
                                        longIntro,
                                        Nickname.of("달리는곰"),
                                        NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.INTRO_TOO_LONG);
    }

    @Test
    void getSettings_returnsCopyThatDoesNotChangeMember() {
        Member m = member("달리는곰");
        MemberSettingsView view = m.getSettings();

        assertThatThrownBy(() -> view.notificationPrefs().put("PLAN_MISSED", false))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.consents().put(ConsentItem.MARKETING, true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(m.getSettings()).isEqualTo(view);
    }

    @Test
    void changeSetting_appliesNonConsentItem() {
        Member m = member("달리는곰");
        SettingOutcome outcome = m.changeSetting(new SettingChange(SettingItem.UNIT, "MI", null));
        assertThat(outcome.applied()).isTrue();
        assertThat(m.getSettings().unit()).isEqualTo(DistanceUnit.MI);

        m.changeSetting(new SettingChange(SettingItem.ALERT, "false", "PLAN_MISSED"));
        assertThat(m.getSettings().notificationPrefs()).containsEntry("PLAN_MISSED", false);
        assertThat(m.allowsPush("PLAN_MISSED")).isFalse();
        assertThat(m.allowsPush("KICKED")).isTrue();
    }

    @Test
    void changeSetting_consentItem_isRejected() {
        Member m = member("달리는곰");
        assertThatThrownBy(
                        () ->
                                m.changeSetting(
                                        new SettingChange(
                                                SettingItem.LOCATION_CONSENT, "false", null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.CONSENT_ITEM_NOT_SETTING);
    }

    @Test
    void changeSetting_watchLinkWithoutHeartRateConsent_needsConsent() {
        Member m = member("달리는곰");
        SettingOutcome outcome =
                m.changeSetting(new SettingChange(SettingItem.WATCH_LINK, "true", null));
        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.requiredConsent()).isEqualTo(ConsentItem.HEART_RATE);
        assertThat(m.getSettings().watchLinked()).isFalse();

        m.giveConsent(ConsentItem.HEART_RATE, NOW);
        assertThat(
                        m.changeSetting(new SettingChange(SettingItem.WATCH_LINK, "true", null))
                                .applied())
                .isTrue();
        assertThat(m.getSettings().watchLinked()).isTrue();
    }

    @Test
    void changeSetting_invalidValue_isRejected() {
        Member m = member("달리는곰");
        assertThatThrownBy(() -> m.changeSetting(new SettingChange(SettingItem.UNIT, "LY", null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.SETTING_INVALID);
    }

    @Test
    void giveAndWithdrawConsent_returnBeforeAndAfter() {
        Member m = member("달리는곰");
        ConsentChange given = m.giveConsent(ConsentItem.MARKETING, NOW);
        assertThat(given.before()).isFalse();
        assertThat(given.after()).isTrue();

        ConsentChange withdrawn = m.withdrawConsent(ConsentItem.LOCATION, NOW.plusMinutes(1));
        assertThat(withdrawn.before()).isTrue();
        assertThat(withdrawn.after()).isFalse();
        assertThat(m.hasConsent(ConsentItem.LOCATION)).isFalse();
    }

    @Test
    void withdrawConsent_requiredNonWithdrawable_isRejected() {
        Member m = member("달리는곰");
        assertThatThrownBy(() -> m.withdrawConsent(ConsentItem.TERMS, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.withdrawConsent(ConsentItem.AGE_14_PLUS, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rememberBattleDistance_onlyRandomBattleDistances() {
        Member m = member("달리는곰");
        m.rememberBattleDistance(StandardDistance.KM10);
        assertThat(m.getSettings().battleSettings().defaultDistance())
                .isEqualTo(StandardDistance.KM10);
        assertThatThrownBy(() -> m.rememberBattleDistance(StandardDistance.HALF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editProfile_changesFieldsAndValidates() {
        Member m = member("달리는곰");
        m.editProfile(
                new ProfileEdit(
                        "빠른곰",
                        null,
                        Gender.MALE,
                        null,
                        "새 소개",
                        Set.of(PersonalityTag.LONG_DISTANCE)),
                Nickname.of("빠른곰"));
        assertThat(m.displayName()).isEqualTo(DisplayName.of("빠른곰"));
        assertThat(m.getTags().tags()).containsExactly(PersonalityTag.LONG_DISTANCE);

        assertThatThrownBy(
                        () ->
                                m.editProfile(
                                        new ProfileEdit(
                                                null, null, null, null, "가".repeat(41), null),
                                        null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                m.editProfile(
                                        new ProfileEdit(
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                Set.of(
                                                        PersonalityTag.SOLO_RUN,
                                                        PersonalityTag.SOCIAL_RUN)),
                                        null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void recordValidRun_placesTierAtThirdAndUpdatesEveryTenth() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 2; i++) {
            assertThat(m.recordValidRun(input(38, i), BOUNDARY).tierChanged()).isFalse();
        }
        TierChange third = m.recordValidRun(input(38, 3), BOUNDARY);
        assertThat(third.previousTier()).isEqualTo(Tier.UNRANKED);
        assertThat(third.tier()).isEqualTo(Tier.GOLD);
        assertThat(m.getTier()).isEqualTo(Tier.GOLD);

        for (int i = 4; i <= 9; i++) {
            m.recordValidRun(input(46, i), BOUNDARY);
            assertThat(m.getTier()).isEqualTo(Tier.GOLD);
        }
        m.recordValidRun(input(46, 10), BOUNDARY);
        // 최근 10회: 38 ×3 + 46 ×7 = 43.6 → 플래티넘
        assertThat(m.getTier()).isEqualTo(Tier.PLATINUM);

        for (int i = 11; i <= 19; i++) {
            m.recordValidRun(input(25, i), BOUNDARY);
        }
        assertThat(m.getTier()).isEqualTo(Tier.PLATINUM);
        m.recordValidRun(input(25, 20), BOUNDARY);
        assertThat(m.getTier()).isEqualTo(Tier.BRONZE);
    }

    @Test
    void recordValidRun_sameOrdinalAgain_changesNothing() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 3; i++) {
            m.recordValidRun(input(38, i), BOUNDARY);
        }
        TierChange again = m.recordValidRun(input(20, 3), BOUNDARY);
        assertThat(again.tierChanged()).isFalse();
        assertThat(m.getTier()).isEqualTo(Tier.GOLD);
        assertThat(m.getLevel()).isEqualTo(1);
    }

    @Test
    void recordValidRun_skippedOrdinals_judgePassedPoints() {
        Member a = member("곰A");
        a.recordValidRun(input(38, 1), BOUNDARY);
        a.recordValidRun(input(38, 2), BOUNDARY);
        a.recordValidRun(input(38, 4), BOUNDARY); // (2 → 4)
        assertThat(a.getTier()).isEqualTo(Tier.GOLD);

        Member b = member("곰B");
        for (int i = 1; i <= 9; i++) {
            b.recordValidRun(input(31, i), BOUNDARY);
        }
        assertThat(b.getTier()).isEqualTo(Tier.SILVER);
        b.recordValidRun(input(46, 11), BOUNDARY); // (9 → 11)
        // 최근 10회: 31 ×9 + 46 = 32.5 → 실버 유지지만 판정은 했다
        assertThat(b.getTier()).isEqualTo(Tier.SILVER);
        b.recordValidRun(input(46, 12), BOUNDARY);
        assertThat(b.getTier()).isEqualTo(Tier.SILVER);

        Member c = member("곰C");
        c.recordValidRun(input(46, 1), BOUNDARY);
        c.recordValidRun(input(46, 2), BOUNDARY);
        c.recordValidRun(input(46, 11), BOUNDARY); // (2 → 11)
        assertThat(c.getTier()).isEqualTo(Tier.MASTER);
    }

    @Test
    void recordValidRun_levelGrowsEvery50km() {
        Member m = member("달리는곰");
        m.recordValidRun(new RunScoreInput(30, Distance.ofKm(49.9), 1), BOUNDARY);
        assertThat(m.getLevel()).isEqualTo(1);
        TierChange change =
                m.recordValidRun(new RunScoreInput(30, Distance.ofKm(0.1), 2), BOUNDARY);
        assertThat(change.level()).isEqualTo(2);
        m.recordValidRun(new RunScoreInput(30, Distance.ofKm(50), 3), BOUNDARY);
        assertThat(m.getLevel()).isEqualTo(3);
    }

    @Test
    void withdraw_wipesIdentity_andSecondCallFails() {
        Member m = member("달리는곰");
        m.withdraw(NOW);

        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(m.displayName()).isEqualTo(DisplayName.WITHDRAWN);
        assertThat(m.ageIn(2026)).isEmpty();
        assertThat(m.hasConsent(ConsentItem.TERMS)).isFalse();
        assertThat(m.withdrawnAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> m.withdraw(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.changeSetting(new SettingChange(SettingItem.UNIT, "MI", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.recordValidRun(input(30, 1), BOUNDARY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void purgeResidual_clearsSettingsProgressTags_andIsIdempotent() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 3; i++) {
            m.recordValidRun(input(38, i), BOUNDARY);
        }
        m.changeSetting(new SettingChange(SettingItem.UNIT, "MI", null));
        assertThatThrownBy(m::purgeResidual).isInstanceOf(IllegalStateException.class);

        m.withdraw(NOW);
        m.purgeResidual();
        MemberSettingsView once = m.getSettings();
        assertThat(m.getTags().isEmpty()).isTrue();
        assertThat(m.getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(m.getLevel()).isEqualTo(1);
        assertThat(once.unit()).isEqualTo(DistanceUnit.KM);
        assertThat(once.recordVisibility()).isEqualTo(RecordVisibility.PRIVATE);
        assertThat(m.displayName()).isEqualTo(DisplayName.WITHDRAWN);
        assertThat(m.withdrawnAt()).isEqualTo(NOW);

        m.purgeResidual();
        assertThat(m.getSettings()).isEqualTo(once);
        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    void ageIn_usesBirthYear() {
        assertThat(member("달리는곰").ageIn(2026)).hasValue(31);
    }

    private static RunScoreInput input(double score, int ordinal) {
        return new RunScoreInput(score, Distance.ofKm(5), ordinal);
    }
}
