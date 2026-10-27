package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("운영 기준값 조회·캐시")
class OperationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0);

    private final InMemoryOperationSettingRepository repository =
            new InMemoryOperationSettingRepository();

    private void store(SettingKey key, String json) {
        OperationSetting setting = OperationSetting.initial(key, NOW);
        setting.change(json, "KAKAO:1", NOW);
        repository.save(setting);
    }

    @Test
    void missingItems_fallBackToSrsDefaults() {
        OperationPolicy policy = new OperationPolicy(repository);

        assertThat(policy.tierBoundary())
                .isEqualTo(new TierBoundary(List.of(30.0, 35.0, 40.0, 45.0)));
        assertThat(policy.rewardRule()).isEqualTo(new RewardRule(10, 20, 200));
        assertThat(policy.selfieLimit()).isEqualTo(new SelfieLimit(1));
        assertThat(policy.features()).isEqualTo(new FeatureToggles(true, true));
        assertThat(policy.shopCatalog().items()).isEmpty();
        assertThat(policy.matchCondition().tierWindow()).isZero();
        assertThat(policy.coachingRule().defaultBpm()).isEqualTo(180);
        assertThat(policy.runimalRule().companionRatio()).isEqualTo(0.5);
        assertThat(policy.courseSimilarityRule().compareCount()).isEqualTo(30);
        assertThat(policy.landmarkTable().landmarks()).isNotEmpty();
        assertThat(policy.virtualRecordRule().openHighScore()).isEqualTo(48);
        assertThat(policy.llmCallLimit().global()).isEqualTo(200);
        assertThat(policy.supplementCap().cap()).isEqualTo(Distance.ofKm(10));
        assertThat(policy.notificationRetention().days()).isEqualTo(30);
        assertThat(policy.calorieCoefficient().kcalPerKm()).isEqualTo(65);
        assertThat(policy.bodyShapeRule().bands()).containsExactly(0, 1, 3, 5);
        assertThat(policy.skinToneRule().bands()).hasSize(5);
        assertThat(policy.crewRunRule().minutesPerKm()).isEqualTo(15);
        assertThat(policy.operatorWhitelist().accounts()).isEmpty();
    }

    @Test
    void storedValues_overrideDefaults_andAreCached() {
        store(SettingKey.SELFIE_LIMIT, "{\"perDay\":3}");
        store(SettingKey.AI_COMMENT_ENABLED, "false");
        store(
                SettingKey.CREW_RUN_LOBBY_OPEN,
                "{\"lobbyOpenBeforeMinutes\":20,\"reminderBeforeMinutes\":5,\"minReady\":3}");
        store(SettingKey.CREW_RUN_END_RULE, "{\"minutesPerKm\":12}");
        OperationPolicy policy = new OperationPolicy(repository);
        int callsAfterStart = repository.findCalls();

        assertThat(policy.selfieLimit().perDay()).isEqualTo(3);
        assertThat(policy.selfieLimit().perDay()).isEqualTo(3);
        assertThat(policy.features()).isEqualTo(new FeatureToggles(true, false));
        assertThat(policy.crewRunRule())
                .isEqualTo(
                        new CrewRunRule(
                                java.time.Duration.ofMinutes(20),
                                java.time.Duration.ofMinutes(5),
                                3,
                                12));
        // 저장된 항목은 시작 때 채운 캐시에서, 없는 항목(SELFIE_ENABLED)은 한 번 읽은 뒤 캐시에서 읽는다
        assertThat(repository.findCalls()).isEqualTo(callsAfterStart + 1);
        policy.features();
        assertThat(repository.findCalls()).isEqualTo(callsAfterStart + 1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("서버 스위치가 꺼져 있으면 운영 기준값과 관계없이 셀카를 막는다(베타 결정 2026-10-23)")
    void selfieServerSwitchOff_overridesStoredToggle() {
        store(SettingKey.SELFIE_ENABLED, "true");
        OperationPolicy policy = new OperationPolicy(repository, false);

        assertThat(policy.features()).isEqualTo(new FeatureToggles(false, true));
    }

    @Test
    void selfieServerSwitchOn_followsStoredToggle() {
        store(SettingKey.SELFIE_ENABLED, "false");

        assertThat(new OperationPolicy(repository, true).features().selfieEnabled()).isFalse();
        assertThat(new OperationPolicy(new InMemoryOperationSettingRepository(), true).features())
                .isEqualTo(new FeatureToggles(true, true));
    }

    @Test
    void itemPrices_overrideShopItemPrices() {
        store(
                SettingKey.SHOP_ITEMS,
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}]}");
        store(SettingKey.ITEM_PRICES, "{\"1\":150,\"9\":10}");
        OperationPolicy policy = new OperationPolicy(repository);

        assertThat(policy.shopCatalog().find(1)).map(ShopItem::price).contains(150);
        assertThat(policy.shopCatalog().find(9)).isEmpty();
    }

    @Test
    void invalidate_reloadsFromRepository() {
        OperationPolicy policy = new OperationPolicy(repository);
        assertThat(policy.selfieLimit().perDay()).isEqualTo(1);

        store(SettingKey.SELFIE_LIMIT, "{\"perDay\":4}");
        assertThat(policy.selfieLimit().perDay()).isEqualTo(1);

        policy.invalidate(SettingKey.SELFIE_LIMIT);
        assertThat(policy.selfieLimit().perDay()).isEqualTo(4);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L8: 저장된 값이 범위 밖이면 기본값을 쓴다")
    void storedOutOfRangeValue_fallsBackToDefault() throws Exception {
        OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, NOW);
        java.lang.reflect.Field value = OperationSetting.class.getDeclaredField("value");
        value.setAccessible(true);
        value.set(setting, "{\"perDay\":0}");
        repository.save(setting);
        assertThat(new OperationPolicy(repository).selfieLimit().perDay()).isEqualTo(1);
    }
}
