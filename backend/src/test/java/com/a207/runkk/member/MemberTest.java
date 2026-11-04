package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static com.a207.runkk.member.MemberFixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import java.util.List;
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
        MemberSettingsView s = m.getSettings();
        assertThat(s.unit()).isEqualTo(DistanceUnit.KM);
        assertThat(s.recordVisibility()).isEqualTo(RecordVisibility.PUBLIC);
        assertThat(s.courseMapShared()).isFalse();
        assertThat(s.profileVisibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThat(s.watchLinked()).isFalse();
        assertThat(s.notificationPrefs())
                .hasSize(21)
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
    void editProfile_introOver40_isRejected() {
        Member m = member("달리는곰");
        assertThatThrownBy(
                        () ->
                                m.editProfile(
                                        new ProfileEdit(null, null, null, "가".repeat(41)),
                                        null,
                                        2026))
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
        m.editProfile(new ProfileEdit("빠른곰", null, Gender.MALE, "새 소개"), Nickname.of("빠른곰"), 2026);
        assertThat(m.displayName()).isEqualTo(DisplayName.of("빠른곰"));
        assertThat(m.intro()).isEqualTo("새 소개");

        assertThatThrownBy(
                        () ->
                                m.editProfile(
                                        new ProfileEdit(null, null, null, "가".repeat(41)),
                                        null,
                                        2026))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(m.intro()).isEqualTo("새 소개");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5738·TC-5739 일반 러닝은 첫 배치를 하지 않는다: 언랭은 몇 번을 뛰어도 언랭, 레벨만 오른다(J24-6, D-484)")
    void recordValidRun_unranked_neverPlacesByNormalRuns() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 12; i++) {
            TierChange change = m.recordValidRun(input(38, i), BOUNDARY, 0);
            assertThat(change.tierChanged()).isFalse();
            assertThat(change.evaluation()).isEmpty();
        }
        assertThat(m.getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(m.getLevel()).isEqualTo(2); // 12 × 5km = 60km
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5742 랭크 티어는 10의 배수마다 최근 10회 평균으로 재평가한다(기존 규칙 그대로)")
    void recordValidRun_ranked_reevaluatesEveryTenth() {
        Member m = member("달리는곰");
        m.confirmPlacement(Tier.GOLD);
        for (int i = 1; i <= 3; i++) {
            m.recordValidRun(input(38, i), BOUNDARY, 0);
        }
        for (int i = 4; i <= 9; i++) {
            m.recordValidRun(input(46, i), BOUNDARY, 0);
            assertThat(m.getTier()).isEqualTo(Tier.GOLD);
        }
        TierChange tenth = m.recordValidRun(input(46, 10), BOUNDARY, 0);
        // 최근 10회: 38 ×3 + 46 ×7 = 43.6 → 플래티넘
        assertThat(m.getTier()).isEqualTo(Tier.PLATINUM);
        assertThat(tenth.evaluation())
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 10));

        for (int i = 11; i <= 19; i++) {
            m.recordValidRun(input(25, i), BOUNDARY, 0);
        }
        assertThat(m.getTier()).isEqualTo(Tier.PLATINUM);
        m.recordValidRun(input(25, 20), BOUNDARY, 0);
        assertThat(m.getTier()).isEqualTo(Tier.BRONZE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5740 3판째가 10번째 순번이면 그 순번에서는 확정만 한다: 확정이 먼저든 러닝 반영이 먼저든 같다(D-484)")
    void recordValidRun_placementAtTenthOrdinal_noReevaluationThere() {
        Member confirmedFirst = member("곰A");
        for (int i = 1; i <= 9; i++) {
            confirmedFirst.recordValidRun(input(46, i), BOUNDARY, 0);
        }
        confirmedFirst.confirmPlacement(Tier.SILVER);
        TierChange tenth = confirmedFirst.recordValidRun(input(46, 10), BOUNDARY, 10);
        assertThat(tenth.evaluation()).isEmpty();
        assertThat(confirmedFirst.getTier()).isEqualTo(Tier.SILVER);

        Member runFirst = member("곰B");
        for (int i = 1; i <= 10; i++) {
            runFirst.recordValidRun(input(46, i), BOUNDARY, 0);
        }
        runFirst.confirmPlacement(Tier.SILVER);
        assertThat(runFirst.getTier()).isEqualTo(Tier.SILVER);

        for (int i = 11; i <= 19; i++) {
            confirmedFirst.recordValidRun(input(46, i), BOUNDARY, 10);
        }
        assertThat(confirmedFirst.getTier()).isEqualTo(Tier.SILVER);
        assertThat(confirmedFirst.recordValidRun(input(46, 20), BOUNDARY, 10).evaluation())
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 20));
        assertThat(confirmedFirst.getTier()).isEqualTo(Tier.MASTER);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5741 확정 순번이 10의 배수 앞이면 그 10의 배수에서 재평가한다")
    void recordValidRun_placementBeforeTenth_reevaluatesAtTenth() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 7; i++) {
            m.recordValidRun(input(30, i), BOUNDARY, 0);
        }
        m.confirmPlacement(Tier.SILVER);
        m.recordValidRun(input(46, 8), BOUNDARY, 7);
        m.recordValidRun(input(46, 9), BOUNDARY, 7);
        assertThat(m.recordValidRun(input(46, 10), BOUNDARY, 7).evaluation()).isPresent();
        // 30 ×7 + 46 ×3 = 34.8 → 실버
        assertThat(m.getTier()).isEqualTo(Tier.SILVER);
    }

    @org.junit.jupiter.params.ParameterizedTest(
            name = "TC-5813 재평가 MC/DC: 랭크={0}, 확정 순번={1}, 순번 9→{2} → 재평가 {3}")
    @org.junit.jupiter.params.provider.CsvSource({
        "true, 0, 10, true",
        "false, 0, 10, false",
        "true, 10, 10, false",
        "true, 0, 9, false"
    })
    void reevaluationCondition_mcdc(
            boolean ranked, int placementOrdinal, int next, boolean expected) {
        Member m = member("판정곰");
        if (ranked) {
            m.confirmPlacement(Tier.BRONZE);
        }
        for (int i = 1; i <= 8; i++) {
            m.recordValidRun(input(46, i), BOUNDARY, placementOrdinal);
        }
        TierChange change = m.recordValidRun(input(46, next), BOUNDARY, placementOrdinal);
        assertThat(change.evaluation().isPresent()).isEqualTo(expected);
    }

    @Test
    void recordValidRun_sameOrdinalAgain_changesNothing() {
        Member m = member("달리는곰");
        m.confirmPlacement(Tier.GOLD);
        for (int i = 1; i <= 10; i++) {
            m.recordValidRun(input(38, i), BOUNDARY, 0);
        }
        TierChange again = m.recordValidRun(input(20, 10), BOUNDARY, 0);
        assertThat(again.tierChanged()).isFalse();
        assertThat(again.evaluation()).isEmpty();
        assertThat(m.getTier()).isEqualTo(Tier.GOLD);
        assertThat(m.getLevel()).isEqualTo(2);
    }

    @Test
    void recordValidRun_skippedOrdinals_judgePassedPoints() {
        Member b = member("곰B");
        b.confirmPlacement(Tier.SILVER);
        for (int i = 1; i <= 9; i++) {
            b.recordValidRun(input(31, i), BOUNDARY, 0);
        }
        assertThat(b.getTier()).isEqualTo(Tier.SILVER);
        b.recordValidRun(input(46, 11), BOUNDARY, 0); // (9 → 11)
        // 최근 10회: 31 ×9 + 46 = 32.5 → 실버 유지지만 판정은 했다
        assertThat(b.getTier()).isEqualTo(Tier.SILVER);

        Member c = member("곰C");
        c.confirmPlacement(Tier.BRONZE);
        c.recordValidRun(input(46, 1), BOUNDARY, 0);
        c.recordValidRun(input(46, 2), BOUNDARY, 0);
        c.recordValidRun(input(46, 11), BOUNDARY, 0); // (2 → 11)
        assertThat(c.getTier()).isEqualTo(Tier.MASTER);
    }

    @Test
    void recordValidRun_levelGrowsEvery50km() {
        Member m = member("달리는곰");
        m.recordValidRun(new RunScoreInput(30, Distance.ofKm(49.9), 1), BOUNDARY, 0);
        assertThat(m.getLevel()).isEqualTo(1);
        TierChange change =
                m.recordValidRun(new RunScoreInput(30, Distance.ofKm(0.1), 2), BOUNDARY, 0);
        assertThat(change.level()).isEqualTo(2);
        m.recordValidRun(new RunScoreInput(30, Distance.ofKm(50), 3), BOUNDARY, 0);
        assertThat(m.getLevel()).isEqualTo(3);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5736·TC-5737 배치고사 확정: 언랭만 랭크 티어로, 레벨·누계는 그대로(05-21 2.1)")
    void confirmPlacement_onlyFromUnrankedToRanked() {
        Member m = member("달리는곰");
        m.recordValidRun(new RunScoreInput(30, Distance.ofKm(60), 1), BOUNDARY, 0);

        assertThatThrownBy(() -> m.confirmPlacement(Tier.UNRANKED))
                .isInstanceOf(IllegalStateException.class);
        m.confirmPlacement(Tier.PLATINUM);

        assertThat(m.getTier()).isEqualTo(Tier.PLATINUM);
        assertThat(m.getLevel()).isEqualTo(2);
        assertThatThrownBy(() -> m.confirmPlacement(Tier.GOLD))
                .isInstanceOf(IllegalStateException.class);

        Member gone = member("떠난곰");
        gone.withdraw(NOW);
        assertThatThrownBy(() -> gone.confirmPlacement(Tier.GOLD))
                .isInstanceOf(IllegalStateException.class);
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
        assertThatThrownBy(() -> m.recordValidRun(input(30, 1), BOUNDARY, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void purgeResidual_clearsSettingsProgressTags_andIsIdempotent() {
        Member m = member("달리는곰");
        for (int i = 1; i <= 3; i++) {
            m.recordValidRun(input(38, i), BOUNDARY, 0);
        }
        m.confirmPlacement(Tier.GOLD);
        m.changeSetting(new SettingChange(SettingItem.UNIT, "MI", null));
        assertThatThrownBy(m::purgeResidual).isInstanceOf(IllegalStateException.class);

        m.withdraw(NOW);
        m.purgeResidual();
        MemberSettingsView once = m.getSettings();
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
