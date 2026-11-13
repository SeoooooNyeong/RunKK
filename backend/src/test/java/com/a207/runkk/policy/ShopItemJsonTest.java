package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.ItemSlot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * W8(S15P31A207-299) 상점 아이템 그림 키·해금 조건. 운영 기준값 SHOP_ITEMS는 옛 형식(그림 키·조건 없음)도 그대로 읽고, 새 항목은 선택이다.
 * contract-W8.md 2.4·3절.
 */
@DisplayName("상점 아이템 그림 키·해금 조건(SHOP_ITEMS)")
class ShopItemJsonTest {

    private static ShopCatalog parse(String items) {
        return (ShopCatalog) SettingKey.SHOP_ITEMS.parse("{\"items\":[" + items + "]}");
    }

    private static void store(
            InMemoryOperationSettingRepository repository, SettingKey key, String json) {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 9, 9, 0);
        OperationSetting setting = OperationSetting.initial(key, now);
        setting.change(json, "KAKAO:1", now);
        repository.save(setting);
    }

    private static boolean valid(String items) {
        return SettingKey.SHOP_ITEMS.validate("{\"items\":[" + items + "]}").valid();
    }

    @Test
    @DisplayName("옛 형식은 그림 키·조건 없이 읽힌다")
    void legacyJson_parses() {
        ShopItem item =
                parse("{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}")
                        .items()
                        .get(0);

        assertThat(item).isEqualTo(new ShopItem(1, "모자", ItemSlot.HAT, 100, true));
        assertThat(item.assetKey()).isNull();
        assertThat(item.unlockCondition()).isEmpty();
    }

    @Test
    @DisplayName("그림 키와 해금 조건을 읽고, null도 없음으로 읽는다")
    void newFields_parse() {
        List<ShopItem> items =
                parse(
                                "{\"id\":6,\"name\":\"완주 기념 티\",\"slot\":\"TOP\",\"price\":300,"
                                    + "\"onSale\":true,\"assetKey\":\"top_finisher_tee\","
                                    + "\"unlock\":{\"kind\":\"LONGEST_RUN\",\"threshold\":21097,\"text\":\"하프"
                                    + " 완주하면 열려요\"}},"
                                    + "{\"id\":7,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,"
                                    + "\"onSale\":true,\"assetKey\":null,\"unlock\":null}")
                        .items();

        assertThat(items.get(0).assetKey()).isEqualTo("top_finisher_tee");
        assertThat(items.get(0).unlockCondition())
                .contains(new ShopUnlock(UnlockKind.LONGEST_RUN, 21_097, "하프 완주하면 열려요"));
        assertThat(items.get(1).assetKey()).isNull();
        assertThat(items.get(1).unlockCondition()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // 모르는 항목
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,\"color\":\"red\"}",
                // 필수 항목 빠짐
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1}",
                // 형식 강제 변환 없음
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":\"10\",\"onSale\":true}",
                "{\"id\":\"1\",\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":\"true\"}",
                // 없는 부위·가격 0
                "{\"id\":1,\"name\":\"a\",\"slot\":\"WINGS\",\"price\":1,\"onSale\":true}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":0,\"onSale\":true}",
                // 그림 키 형식(영문 소문자·숫자·밑줄 1~40자)
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,\"assetKey\":\"Top-1\"}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,\"assetKey\":\"\"}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,\"assetKey\":3}",
                // 조건: 모르는 종류, 기준 0, 문구 빔·40자 초과
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,"
                        + "\"unlock\":{\"kind\":\"BATTLE_WIN\",\"threshold\":1,\"text\":\"t\"}}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,"
                        + "\"unlock\":{\"kind\":\"RUN_COUNT\",\"threshold\":0,\"text\":\"t\"}}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,"
                        + "\"unlock\":{\"kind\":\"RUN_COUNT\",\"threshold\":1,\"text\":\" \"}}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,"
                        + "\"unlock\":{\"kind\":\"RUN_COUNT\",\"threshold\":1,"
                        + "\"text\":\"12345678901234567890123456789012345678901\"}}",
                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true,"
                        + "\"unlock\":{\"kind\":\"RUN_COUNT\",\"threshold\":1}}",
                // 아이템이 객체가 아님
                "1"
            })
    @DisplayName("잘못된 아이템 JSON은 형식 오류로 거부한다")
    void invalidJson_rejected(String item) {
        assertThat(valid(item)).isFalse();
    }

    @Test
    @DisplayName("예전 변환기처럼 소수점 아래가 0인 숫자(300.0)는 받고, 버림이 생기는 값(300.5)은 거부한다")
    void wholeNumberFloats() {
        ShopItem item =
                parse(
                                "{\"id\":1.0,\"name\":\"a\",\"slot\":\"HAT\",\"price\":300.0,\"onSale\":true}")
                        .items()
                        .get(0);
        assertThat(item.id()).isEqualTo(1);
        assertThat(item.price()).isEqualTo(300);
        assertThat(
                        valid(
                                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":300.5,\"onSale\":true}"))
                .isFalse();
        assertThat(
                        valid(
                                "{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":3000000000,"
                                        + "\"onSale\":true}"))
                .isFalse();
    }

    @Test
    @DisplayName("V62 기본 상점 목록과 import-local.sql 목록이 같고 SHOP_ITEMS 검사를 통과한다(4부위, 잠긴 아이템 3개)")
    void seedCatalog_isValid() throws java.io.IOException {
        java.util.regex.Pattern json =
                java.util.regex.Pattern.compile("'(\\{\"items\":.*?\\]\\})'");
        String sql =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "src/main/resources/db/migration/V62__shop_items_seed.sql"));
        String local =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of("src/main/resources/import-local.sql"));
        java.util.regex.Matcher seed = json.matcher(sql);
        java.util.regex.Matcher localSeed = json.matcher(local);
        assertThat(seed.find()).isTrue();
        assertThat(localSeed.find()).isTrue();
        assertThat(localSeed.group(1)).isEqualTo(seed.group(1));

        assertThat(SettingKey.SHOP_ITEMS.validate(seed.group(1)).valid()).isTrue();
        ShopCatalog catalog = (ShopCatalog) SettingKey.SHOP_ITEMS.parse(seed.group(1));
        assertThat(catalog.items())
                .hasSize(16)
                .allSatisfy(i -> assertThat(i.assetKey()).isNotNull());
        assertThat(catalog.items().stream().map(ShopItem::slot).distinct())
                .containsExactlyInAnyOrder(ItemSlot.values());
        assertThat(catalog.items().stream().filter(i -> i.unlock() != null))
                .extracting(ShopItem::id)
                .containsExactly(6L, 23L, 34L);
        // 이름은 앱이 실제로 그리는 Rive 의상 모양에 맞춘다(실기기 2차 시험 관찰, contract-W8 6.3).
        // 모자(31~34)는 Rive 슬롯이 없어 그대로다
        assertThat(
                        catalog.items().stream()
                                .filter(i -> i.slot() != ItemSlot.HAT)
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                ShopItem::id, ShopItem::name)))
                .isEqualTo(
                        java.util.Map.ofEntries(
                                java.util.Map.entry(1L, "민트 싱글렛"),
                                java.util.Map.entry(2L, "새벽 바람막이"),
                                java.util.Map.entry(3L, "크루 카라티"),
                                java.util.Map.entry(4L, "겨울 후디"),
                                java.util.Map.entry(5L, "코랄 집업 후드"),
                                java.util.Map.entry(6L, "완주 기념 맨투맨"),
                                java.util.Map.entry(11L, "러닝 반바지"),
                                java.util.Map.entry(12L, "조거 팬츠"),
                                java.util.Map.entry(13L, "러닝 레깅스"),
                                java.util.Map.entry(21L, "캔버스 로우탑"),
                                java.util.Map.entry(22L, "코랄 러닝화"),
                                java.util.Map.entry(23L, "트레일 러닝화")));
    }

    @Test
    @DisplayName("ITEM_PRICES로 가격을 덮어도 그림 키·조건은 남는다")
    void withPrice_keepsAssetAndUnlock() {
        ShopUnlock unlock = new ShopUnlock(UnlockKind.RUN_COUNT, 10, "10번 달리면 열려요");
        ShopItem item = new ShopItem(34, "캡", ItemSlot.HAT, 300, true, "hat_cap", unlock);

        assertThat(item.withPrice(150))
                .isEqualTo(new ShopItem(34, "캡", ItemSlot.HAT, 150, true, "hat_cap", unlock));
        assertThatThrownBy(() -> item.withPrice(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OperationPolicy.shopCatalog가 ITEM_PRICES를 덮어도 그림 키·조건을 잃지 않는다")
    void operationPolicy_keepsAssetAndUnlock() {
        InMemoryOperationSettingRepository repository = new InMemoryOperationSettingRepository();
        store(
                repository,
                SettingKey.SHOP_ITEMS,
                "{\"items\":[{\"id\":34,\"name\":\"캡\",\"slot\":\"HAT\",\"price\":300,"
                        + "\"onSale\":true,\"assetKey\":\"hat_cap\",\"unlock\":{\"kind\":"
                        + "\"RUN_COUNT\",\"threshold\":10,\"text\":\"10번 달리면 열려요\"}}]}");
        store(repository, SettingKey.ITEM_PRICES, "{\"34\":150}");

        ShopItem item = new OperationPolicy(repository).shopCatalog().find(34).orElseThrow();

        assertThat(item.price()).isEqualTo(150);
        assertThat(item.assetKey()).isEqualTo("hat_cap");
        assertThat(item.unlockCondition()).map(ShopUnlock::threshold).contains(10L);
    }
}
