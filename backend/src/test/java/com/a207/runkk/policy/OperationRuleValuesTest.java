package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagCategory;
import com.a207.runkk.common.Tier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 14장 순서 1 운영 기준 값 객체(6.1.9 표). 기본값은 srs.md 예시값. */
class OperationRuleValuesTest {

    private static <T> T defaultOf(SettingKey key, Class<T> type) {
        return type.cast(key.parse(key.defaultValue()));
    }

    // TierBoundary
    @ParameterizedTest
    @CsvSource({
        "0, BRONZE",
        "29.99, BRONZE",
        "30, SILVER",
        "34.9, SILVER",
        "35, GOLD",
        "40, PLATINUM",
        "44.99, PLATINUM",
        "45, MASTER",
        "60, MASTER"
    })
    void tierBoundary_tierOf(double score, Tier expected) {
        TierBoundary boundary = defaultOf(SettingKey.TIER_BOUNDARY, TierBoundary.class);
        assertThat(boundary.tierOf(score)).isEqualTo(expected);
    }

    @Test
    void tierBoundary_midScoreOf_usesOpenIntervalsFromVirtualRecordRule() {
        TierBoundary boundary = defaultOf(SettingKey.TIER_BOUNDARY, TierBoundary.class);
        VirtualRecordRule rule = defaultOf(SettingKey.VIRTUAL_RECORD_RULE, VirtualRecordRule.class);
        assertThat(boundary.midScoreOf(Tier.BRONZE, rule)).isEqualTo(28.5);
        assertThat(boundary.midScoreOf(Tier.SILVER, rule)).isEqualTo(32.5);
        assertThat(boundary.midScoreOf(Tier.PLATINUM, rule)).isEqualTo(42.5);
        assertThat(boundary.midScoreOf(Tier.MASTER, rule)).isEqualTo(46.5);
        assertThatThrownBy(() -> boundary.midScoreOf(Tier.UNRANKED, rule))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tierBoundary_requiresFourAscendingBounds() {
        assertThatThrownBy(() -> new TierBoundary(List.of(30.0, 35.0, 40.0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TierBoundary(List.of(30.0, 30.0, 40.0, 45.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // MatchCondition
    @Test
    void matchCondition_accepts() {
        MatchCondition sameTier = defaultOf(SettingKey.MATCH_CONDITION, MatchCondition.class);
        assertThat(sameTier.ghostAfter()).isEqualTo(Duration.ofSeconds(15));
        assertThat(sameTier.accepts(Tier.GOLD, Tier.GOLD)).isTrue();
        assertThat(sameTier.accepts(Tier.GOLD, Tier.SILVER)).isFalse();
        MatchCondition window1 = new MatchCondition(1, Duration.ofSeconds(15));
        assertThat(window1.accepts(Tier.GOLD, Tier.SILVER)).isTrue();
        assertThat(window1.accepts(Tier.GOLD, Tier.BRONZE)).isFalse();
        assertThat(window1.accepts(Tier.UNRANKED, Tier.BRONZE)).isFalse();
        assertThat(window1.accepts(Tier.UNRANKED, Tier.UNRANKED)).isTrue();
    }

    // RewardRule
    @Test
    void rewardRule_amountForRun_floorsKmTimesPerKm() {
        RewardRule rule = defaultOf(SettingKey.REWARD_RULE, RewardRule.class);
        assertThat(rule.amountForRun(Distance.ofMeters(5_999))).isEqualTo(59);
        assertThat(rule.amountForRun(Distance.ofMeters(10_000))).isEqualTo(100);
        assertThat(rule.battleWin()).isEqualTo(20);
        assertThat(rule.dailyCap()).isEqualTo(200);
    }

    // CoachingRule
    @Test
    void coachingRule_thresholdFor() {
        CoachingRule rule = defaultOf(SettingKey.COACHING_RULE, CoachingRule.class);
        assertThat(rule.thresholdFor(OptionalInt.of(30))).isEqualTo(171);
        assertThat(rule.thresholdFor(OptionalInt.empty())).isEqualTo(180);
        assertThat(rule.holdSeconds()).isEqualTo(30);
        assertThat(rule.cooldownSeconds()).isEqualTo(60);
    }

    // CalorieCoefficient
    @Test
    void calorieCoefficient_estimate() {
        CalorieCoefficient c = defaultOf(SettingKey.CALORIE_COEFFICIENT, CalorieCoefficient.class);
        assertThat(c.estimate(Distance.ofMeters(5_000))).isEqualTo(325);
        assertThat(c.estimate(Distance.ofMeters(1_234))).isEqualTo(80);
        assertThat(c.estimate(Distance.ZERO)).isZero();
    }

    // CrewRunRule
    @Test
    void crewRunRule_closesAt() {
        CrewRunRule rule =
                new OperationPolicy(new InMemoryOperationSettingRepository()).crewRunRule();
        LocalDateTime departed = LocalDateTime.of(2026, 10, 20, 19, 0);
        assertThat(rule.lobbyOpenBefore()).isEqualTo(Duration.ofMinutes(30));
        assertThat(rule.reminderBefore()).isEqualTo(Duration.ofMinutes(10));
        assertThat(rule.minReady()).isEqualTo(2);
        assertThat(rule.closesAt(departed, StandardDistance.KM5))
                .isEqualTo(departed.plusMinutes(75));
        assertThat(rule.closesAt(departed, StandardDistance.KM1))
                .isEqualTo(departed.plusMinutes(15));
    }

    // NotificationRetention
    @Test
    void notificationRetention_isExpired_strictlyAfterDays() {
        NotificationRetention r =
                defaultOf(SettingKey.NOTIFICATION_RETENTION, NotificationRetention.class);
        LocalDateTime occurred = LocalDateTime.of(2026, 9, 1, 12, 0);
        assertThat(r.isExpired(occurred, occurred.plusDays(30))).isFalse();
        assertThat(r.isExpired(occurred, occurred.plusDays(30).plusSeconds(1))).isTrue();
    }

    // BodyShapeRule: 0 / 1~2 / 3~4 / 5+ → 1~4단계
    @ParameterizedTest
    @CsvSource({"0, 1", "1, 2", "2, 2", "3, 3", "4, 3", "5, 4", "7, 4"})
    void bodyShapeRule_stageFor(int runsIn7Days, int stage) {
        BodyShapeRule rule = defaultOf(SettingKey.BODY_SHAPE_RULE, BodyShapeRule.class);
        assertThat(rule.stageFor(runsIn7Days)).isEqualTo(stage);
        assertThat(rule.restDaysBeforeDecline()).isEqualTo(4);
    }

    // SkinToneRule: 새벽 04~07, 아침 07~11, 낮 11~17, 저녁 17~21, 밤 21~04 → 0~4
    @ParameterizedTest
    @CsvSource({
        "04:00, 0",
        "06:59, 0",
        "07:00, 1",
        "10:59, 1",
        "11:00, 2",
        "16:59, 2",
        "17:00, 3",
        "21:00, 4",
        "23:59, 4",
        "00:00, 4",
        "03:59, 4"
    })
    void skinToneRule_toneOf(LocalTime time, int band) {
        SkinToneRule rule = defaultOf(SettingKey.SKIN_TONE_RULE, SkinToneRule.class);
        assertThat(rule.toneOf(time)).isEqualTo(band);
    }

    // RunimalRule
    @Test
    void runimalRule_thresholdPerAxis() {
        RunimalRule rule = defaultOf(SettingKey.RUNIMAL_RULE, RunimalRule.class);
        assertThat(rule.threshold(TagCategory.COMPANION)).isEqualTo(0.5);
        assertThat(rule.threshold(TagCategory.DISTANCE)).isEqualTo(0.5);
        assertThat(rule.threshold(TagCategory.COURSE)).isEqualTo(0.6);
        assertThat(rule.threshold(TagCategory.HABIT)).isEqualTo(0.6);
        assertThat(rule.shortDistance()).isEqualTo(Distance.ofKm(3));
        assertThat(rule.habitWindow()).isEqualTo(Duration.ofHours(2));
        assertThatThrownBy(() -> rule.threshold(TagCategory.GOAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ShopCatalog
    @Test
    void shopCatalog_queries() {
        ShopCatalog catalog =
                new ShopCatalog(
                        List.of(
                                new ShopItem(1, "모자", ItemSlot.HAT, 100, true),
                                new ShopItem(2, "신발", ItemSlot.SHOES, 200, false)));
        assertThat(catalog.find(2)).map(ShopItem::name).contains("신발");
        assertThat(catalog.find(9)).isEmpty();
        assertThat(catalog.bySlot(Optional.of(ItemSlot.HAT)))
                .extracting(ShopItem::id)
                .containsExactly(1L);
        assertThat(catalog.bySlot(Optional.empty())).hasSize(2);
        assertThat(catalog.slotOf(1)).contains(ItemSlot.HAT);
        assertThat(catalog.slotOf(9)).isEmpty();
    }

    @Test
    void shopCatalog_allowsTransitionFrom() {
        ShopCatalog before =
                new ShopCatalog(List.of(new ShopItem(1, "모자", ItemSlot.HAT, 100, true)));
        assertThat(
                        new ShopCatalog(List.of(new ShopItem(1, "모자2", ItemSlot.HAT, 90, false)))
                                .allowsTransitionFrom(before))
                .isTrue();
        assertThat(
                        new ShopCatalog(List.of(new ShopItem(1, "모자", ItemSlot.TOP, 100, true)))
                                .allowsTransitionFrom(before))
                .isFalse();
        assertThat(new ShopCatalog(List.of()).allowsTransitionFrom(before)).isFalse();
    }

    @Test
    void shopItem_validation() {
        assertThatThrownBy(() -> new ShopItem(1, "x", ItemSlot.HAT, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShopItem(1, "x", null, 10, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // OperatorWhitelist
    @Test
    void operatorWhitelist_contains() {
        OperatorWhitelist list = new OperatorWhitelist(Map.of("KAKAO", Set.of("123")));
        assertThat(list.contains("KAKAO", "123")).isTrue();
        assertThat(list.contains("GOOGLE", "123")).isFalse();
        assertThat(list.contains("KAKAO", "999")).isFalse();
        assertThat(
                        defaultOf(SettingKey.OPERATOR_WHITELIST, OperatorWhitelist.class)
                                .contains("KAKAO", "1"))
                .isFalse();
    }

    // 나머지 기본값
    @Test
    void otherDefaults_matchSrsExamples() {
        CourseSimilarityRule course =
                defaultOf(SettingKey.COURSE_SIMILARITY, CourseSimilarityRule.class);
        assertThat(course).isEqualTo(new CourseSimilarityRule(0.8, 50, 30));
        VirtualRecordRule virtual =
                defaultOf(SettingKey.VIRTUAL_RECORD_RULE, VirtualRecordRule.class);
        assertThat(virtual.paceOffsets()).containsExactly(-0.05, -0.025, 0.0, 0.025, 0.05);
        assertThat(virtual.distances()).hasSize(4);
        assertThat(defaultOf(SettingKey.SELFIE_LIMIT, SelfieLimit.class).perDay()).isEqualTo(1);
        assertThat(defaultOf(SettingKey.LLM_DAILY_LIMIT, LlmCallLimit.class))
                .isEqualTo(new LlmCallLimit(1, 200));
        assertThat(defaultOf(SettingKey.SUPPLEMENT_CAP, SupplementCap.class).cap())
                .isEqualTo(Distance.ofKm(10));
        assertThat(new FeatureToggles(true, false).aiCommentEnabled()).isFalse();
        assertThat(new LandmarkProgress(LandmarkStage.DOMESTIC, "인천", "x").name()).isEqualTo("인천");
        assertThat(new SettingChangeResult(SettingKey.SELFIE_LIMIT, "a", "b").after())
                .isEqualTo("b");
        assertThat(0.1 + 0.2).isCloseTo(0.3, within(1e-9));
    }
}
