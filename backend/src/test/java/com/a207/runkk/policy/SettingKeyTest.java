package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 14장 순서 1 열거형 다형성: SettingKey 상수별 validate·parse·validateTransition(FR-174, D-44, D-62). */
@org.junit.jupiter.api.DisplayName("운영 기준 항목 검사·변환")
class SettingKeyTest {

    @Test
    void has21SettingItemsPlusOperatorWhitelist() {
        // v0.14 CREW_RELEASED(FR-223, D-247), AVATAR_RELEASED(D138), RUNBODY_ENABLED(05-19 D-440)
        // BODY_SHAPE_RELEASED: 체형 변화는 2차 배포에서 뺀다([임시:D-성-218])
        assertThat(SettingKey.values()).hasSize(28);
        assertThat(Arrays.stream(SettingKey.values()).map(Enum::name))
                .contains(
                        "OPERATOR_WHITELIST",
                        "LLM_DAILY_LIMIT",
                        "SHOP_ITEMS",
                        "ITEM_PRICES",
                        "CREW_RELEASED",
                        "AVATAR_RELEASED",
                        "BODY_SHAPE_RELEASED",
                        "RUNBODY_ENABLED",
                        "COACH_ENABLED",
                        "COACH_DAILY_LIMIT");
    }

    @ParameterizedTest
    @EnumSource(
            value = SettingKey.class,
            names = "OPERATOR_WHITELIST",
            mode = EnumSource.Mode.EXCLUDE)
    void defaultValue_isValidAndParses(SettingKey key) {
        assertThat(key.validate(key.defaultValue()).valid()).as(key.name()).isTrue();
        assertThat(key.parse(key.defaultValue())).as(key.name()).isNotNull();
    }

    @Test
    void operatorWhitelist_defaultIsEmpty_butChangesNeedAtLeastOneAccount() {
        SettingKey key = SettingKey.OPERATOR_WHITELIST;
        assertThat(((OperatorWhitelist) key.parse(key.defaultValue())).accounts()).isEmpty();
        assertThat(key.validate(key.defaultValue()).valid()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SettingKey.class)
    void malformedJson_isInvalid(SettingKey key) {
        assertThat(key.validate("{not json").valid()).isFalse();
        assertThat(key.validate(null).valid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "MATCH_CONDITION|{\"tierWindow\":2,\"ghostAfter\":\"PT60S\"}|true",
                "MATCH_CONDITION|{\"tierWindow\":3,\"ghostAfter\":\"PT15S\"}|false",
                "MATCH_CONDITION|{\"tierWindow\":0,\"ghostAfter\":\"PT4S\"}|false",
                "MATCH_CONDITION|{\"tierWindow\":0,\"ghostAfter\":\"PT61S\"}|false",
                "REWARD_RULE|{\"perKm\":10,\"battleWin\":20,\"dailyCap\":200}|true",
                "REWARD_RULE|{\"perKm\":0,\"battleWin\":20,\"dailyCap\":200}|false",
                "ITEM_PRICES|{\"1\":100,\"2\":50}|true",
                "ITEM_PRICES|{\"1\":0}|false",
                "SHOP_ITEMS|{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}]}|true",
                "SHOP_ITEMS|{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":0,\"onSale\":true}]}|false",
                "SHOP_ITEMS|{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"WINGS\",\"price\":10,\"onSale\":true}]}|false",
                "SHOP_ITEMS|{\"items\":[{\"id\":1,\"name\":\"a\",\"slot\":\"HAT\",\"price\":1,\"onSale\":true},{\"id\":1,\"name\":\"b\",\"slot\":\"TOP\",\"price\":1,\"onSale\":true}]}|false",
                "BODY_SHAPE_RULE|{\"bands\":[0,1,3,5],\"restDaysBeforeDecline\":1}|true",
                "BODY_SHAPE_RULE|{\"bands\":[0,3,1,5],\"restDaysBeforeDecline\":4}|false",
                "BODY_SHAPE_RULE|{\"bands\":[0,1,3],\"restDaysBeforeDecline\":4}|false",
                "BODY_SHAPE_RULE|{\"bands\":[0,1,3,5],\"restDaysBeforeDecline\":0}|false",
                "SKIN_TONE_RULE|{\"bands\":[\"05:00\",\"08:00\",\"12:00\",\"18:00\",\"22:00\"]}|true",
                "SKIN_TONE_RULE|{\"bands\":[\"05:00\",\"08:00\",\"12:00\",\"18:00\"]}|false",
                "SKIN_TONE_RULE|{\"bands\":[\"05:00\",\"04:00\",\"12:00\",\"18:00\",\"22:00\"]}|false",
                "COACHING_RULE|{\"ratio\":0.5,\"defaultBpm\":220,\"holdSeconds\":30,\"cooldownSeconds\":60}|true",
                "COACHING_RULE|{\"ratio\":1.01,\"defaultBpm\":180,\"holdSeconds\":30,\"cooldownSeconds\":60}|false",
                "COACHING_RULE|{\"ratio\":0.9,\"defaultBpm\":99,\"holdSeconds\":30,\"cooldownSeconds\":60}|false",
                "TIER_BOUNDARY|{\"lowerBounds\":[31,36,41,46]}|true",
                "TIER_BOUNDARY|{\"lowerBounds\":[31,30,41,46]}|false",
                "TIER_BOUNDARY|{\"lowerBounds\":[31,36,41]}|false",
                "RUNIMAL_RULE|{\"companionRatio\":1.0,\"shortDistance\":{\"meters\":3000},\"shortRatio\":0,\"sameCourseRatio\":0.6,\"habitWindow\":\"PT2H\",\"habitRatio\":0.6}|true",
                "RUNIMAL_RULE|{\"companionRatio\":1.1,\"shortDistance\":{\"meters\":3000},\"shortRatio\":0.5,\"sameCourseRatio\":0.6,\"habitWindow\":\"PT2H\",\"habitRatio\":0.6}|false",
                "RUNIMAL_RULE|{\"companionRatio\":0.5,\"shortDistance\":{\"meters\":0},\"shortRatio\":0.5,\"sameCourseRatio\":0.6,\"habitWindow\":\"PT2H\",\"habitRatio\":0.6}|false",
                "COURSE_SIMILARITY|{\"coverage\":1.0,\"radiusMeters\":1,\"compareCount\":100}|true",
                "COURSE_SIMILARITY|{\"coverage\":0.8,\"radiusMeters\":0,\"compareCount\":30}|false",
                "COURSE_SIMILARITY|{\"coverage\":0.8,\"radiusMeters\":50,\"compareCount\":101}|false",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"INCHEON\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|true",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"DAEJEON\",\"stage\":\"DOMESTIC\",\"name\":\"대전\",\"distance\":{\"meters\":280000},\"roundTrip\":true},{\"key\":\"INCHEON\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|false",
                "LANDMARKS|{\"landmarks\":[]}|false",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"LM_1\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|true",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"incheon\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|false",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"INCHEON\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true},{\"key\":\"INCHEON\",\"stage\":\"DOMESTIC\",\"name\":\"춘천\",\"distance\":{\"meters\":150000},\"roundTrip\":true}]}|false",
                "LANDMARKS|{\"landmarks\":[{\"key\":\"\",\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|false",
                "LANDMARKS|{\"landmarks\":[{\"stage\":\"DOMESTIC\",\"name\":\"인천\",\"distance\":{\"meters\":60000},\"roundTrip\":true}]}|false",
                "VIRTUAL_RECORD_RULE|{\"openLowScore\":27,\"openHighScore\":48,\"paceOffsets\":[-0.05,-0.025,0,0.025,0.05],\"distances\":[{\"meters\":1000}]}|true",
                "VIRTUAL_RECORD_RULE|{\"openLowScore\":0,\"openHighScore\":48,\"paceOffsets\":[-0.05,-0.025,0,0.025,0.05],\"distances\":[{\"meters\":1000}]}|false",
                "VIRTUAL_RECORD_RULE|{\"openLowScore\":27,\"openHighScore\":48,\"paceOffsets\":[-0.05,0,0.05],\"distances\":[{\"meters\":1000}]}|false",
                "SELFIE_ENABLED|false|true",
                "SELFIE_ENABLED|\"yes\"|false",
                "AI_COMMENT_ENABLED|true|true",
                "SELFIE_LIMIT|{\"perDay\":2}|true",
                "SELFIE_LIMIT|{\"perDay\":0}|false",
                "LLM_DAILY_LIMIT|{\"perMember\":1,\"global\":200}|true",
                "LLM_DAILY_LIMIT|{\"perMember\":1,\"global\":201}|false",
                "LLM_DAILY_LIMIT|{\"perMember\":0,\"global\":100}|false",
                "SUPPLEMENT_CAP|{\"cap\":{\"meters\":5000}}|true",
                "SUPPLEMENT_CAP|{\"cap\":{\"meters\":0}}|false",
                "NOTIFICATION_RETENTION|{\"days\":1}|true",
                "NOTIFICATION_RETENTION|{\"days\":0}|false",
                "CALORIE_COEFFICIENT|{\"kcalPerKm\":10}|true",
                "CALORIE_COEFFICIENT|{\"kcalPerKm\":200}|true",
                "CALORIE_COEFFICIENT|{\"kcalPerKm\":9}|false",
                "CALORIE_COEFFICIENT|{\"kcalPerKm\":201}|false",
                "CREW_RUN_LOBBY_OPEN|{\"lobbyOpenBeforeMinutes\":30,\"reminderBeforeMinutes\":10,\"minReady\":2}|true",
                "CREW_RUN_LOBBY_OPEN|{\"lobbyOpenBeforeMinutes\":0,\"reminderBeforeMinutes\":10,\"minReady\":2}|false",
                "CREW_RUN_END_RULE|{\"minutesPerKm\":15}|true",
                "CREW_RUN_END_RULE|{\"minutesPerKm\":0}|false",
                "OPERATOR_WHITELIST|{\"accounts\":{\"KAKAO\":[\"123\"]}}|true",
                "OPERATOR_WHITELIST|{\"accounts\":{}}|false",
                "OPERATOR_WHITELIST|{\"accounts\":{\"KAKAO\":[]}}|false"
            })
    void validate_checksFormatAndRange(SettingKey key, String json, boolean expected) {
        assertThat(key.validate(json).valid()).isEqualTo(expected);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6164 기본 랜드마크 표는 키를 가진 채 통과하고 키는 D-107 목록이다")
    void landmarks_defaultTableHasKeys() {
        assertThat(SettingKey.LANDMARKS.validate(SettingKey.LANDMARKS.defaultValue()).valid())
                .isTrue();
        LandmarkTable table =
                (LandmarkTable) SettingKey.LANDMARKS.parse(SettingKey.LANDMARKS.defaultValue());
        assertThat(table.landmarks()).hasSize(30);
        assertThat(table.landmarks().stream().map(Landmark::key).toList())
                .containsExactly(
                        "INCHEON",
                        "CHUNCHEON",
                        "DAEJEON",
                        "JEONJU",
                        "YEOSU",
                        "BUSAN",
                        "JEJU",
                        "BEIJING",
                        "TOKYO",
                        "TAIPEI",
                        "HONG_KONG",
                        "HANOI",
                        "BANGKOK",
                        "KUALA_LUMPUR",
                        "HONOLULU",
                        "SYDNEY",
                        "LONDON",
                        "PARIS",
                        "LOS_ANGELES",
                        "NEW_YORK",
                        "SANTIAGO",
                        "EARTH_LAP",
                        "EARTH_2_LAPS",
                        "KM_100K",
                        "KM_200K",
                        "MOON_APPROACH",
                        "MOON",
                        "KM_500K",
                        "MOON_ROUND_TRIP",
                        "KM_1M");
        assertThat(table.landmarks().stream().filter(l -> l.stage() != LandmarkStage.CREW_TOTAL))
                .hasSize(22);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "D-성-129 기본 랜드마크에 북한 도시·강릉이 없고 국내 앞부분은 인천 60 → 춘천 150 → 대전 280 왕복이다")
    void landmarks_domesticHeadIsIncheonChuncheonDaejeon() {
        LandmarkTable table =
                (LandmarkTable) SettingKey.LANDMARKS.parse(SettingKey.LANDMARKS.defaultValue());
        java.util.List<String> keys = table.landmarks().stream().map(Landmark::key).toList();
        java.util.List<String> names = table.landmarks().stream().map(Landmark::name).toList();
        assertThat(keys).doesNotContain("KAESONG", "PYONGYANG", "SINUIJU", "WONSAN", "GANGNEUNG");
        assertThat(names).doesNotContain("개성", "평양", "신의주", "원산", "강릉");
        assertThat(table.landmarks().subList(0, 3))
                .extracting(
                        Landmark::key,
                        Landmark::name,
                        Landmark::stage,
                        Landmark::distance,
                        Landmark::roundTrip)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "INCHEON",
                                "인천",
                                LandmarkStage.DOMESTIC,
                                com.a207.runkk.common.Distance.ofKm(60),
                                true),
                        org.assertj.core.groups.Tuple.tuple(
                                "CHUNCHEON",
                                "춘천",
                                LandmarkStage.DOMESTIC,
                                com.a207.runkk.common.Distance.ofKm(150),
                                true),
                        org.assertj.core.groups.Tuple.tuple(
                                "DAEJEON",
                                "대전",
                                LandmarkStage.DOMESTIC,
                                com.a207.runkk.common.Distance.ofKm(280),
                                true));
    }

    @Test
    void invalidResult_carriesReason() {
        ValidationResult result = SettingKey.CALORIE_COEFFICIENT.validate("{\"kcalPerKm\":9}");
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isNotBlank();
        assertThat(ValidationResult.ok().valid()).isTrue();
    }

    @Test
    void parse_returnsTypedValueObject() {
        assertThat(SettingKey.TIER_BOUNDARY.parse(SettingKey.TIER_BOUNDARY.defaultValue()))
                .isEqualTo(new TierBoundary(java.util.List.of(30.0, 35.0, 40.0, 45.0)));
        assertThat(SettingKey.ITEM_PRICES.parse("{\"7\":300}")).isEqualTo(Map.of(7L, 300));
        assertThat(SettingKey.SELFIE_ENABLED.parse("false")).isEqualTo(false);
    }

    @Test
    void validateTransition_passesByDefault() {
        assertThat(
                        SettingKey.REWARD_RULE
                                .validateTransition(
                                        SettingKey.REWARD_RULE.defaultValue(),
                                        "{\"perKm\":1,\"battleWin\":1,\"dailyCap\":1}")
                                .valid())
                .isTrue();
    }

    private static final String CATALOG_BEFORE =
            "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true},"
                + "{\"id\":2,\"name\":\"신발\",\"slot\":\"SHOES\",\"price\":200,\"onSale\":true}]}";

    @Test
    void shopItems_transition_rejectsDeletedIdOrChangedSlot_allowsStopSale() {
        String deleted =
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}]}";
        String slotChanged =
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"TOP\",\"price\":100,\"onSale\":true},"
                    + "{\"id\":2,\"name\":\"신발\",\"slot\":\"SHOES\",\"price\":200,\"onSale\":true}]}";
        String stopSaleAndAdd =
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":false},"
                    + "{\"id\":2,\"name\":\"신발\",\"slot\":\"SHOES\",\"price\":250,\"onSale\":true},"
                    + "{\"id\":3,\"name\":\"상의\",\"slot\":\"TOP\",\"price\":50,\"onSale\":true}]}";
        SettingKey key = SettingKey.SHOP_ITEMS;
        assertThat(key.validateTransition(CATALOG_BEFORE, deleted).valid()).isFalse();
        assertThat(key.validateTransition(CATALOG_BEFORE, slotChanged).valid()).isFalse();
        assertThat(key.validateTransition(CATALOG_BEFORE, stopSaleAndAdd).valid()).isTrue();
        assertThat(key.validateTransition(CATALOG_BEFORE, "{bad").valid()).isFalse();
    }

    @Test
    void itemPrices_transition_rejectsDeletedId() {
        SettingKey key = SettingKey.ITEM_PRICES;
        assertThat(key.validateTransition("{\"1\":100,\"2\":50}", "{\"1\":120}").valid()).isFalse();
        assertThat(key.validateTransition("{\"1\":100}", "{\"1\":120,\"2\":50}").valid()).isTrue();
        assertThat(key.validateTransition("{\"1\":100}", "oops").valid()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L9: 체형 구간의 첫 하한은 0이어야 한다")
    void bodyShapeRule_firstBandMustBeZero() {
        assertThat(
                        SettingKey.BODY_SHAPE_RULE
                                .validate("{\"bands\":[1,2,3,5],\"restDaysBeforeDecline\":4}")
                                .valid())
                .isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L4: 크루런 운영값은 이름 있는 형식으로 읽는다")
    void crewRunSettings_parseIntoTypedRecords() {
        assertThat(
                        SettingKey.CREW_RUN_LOBBY_OPEN.parse(
                                SettingKey.CREW_RUN_LOBBY_OPEN.defaultValue()))
                .isEqualTo(new CrewRunLobby(30, 10, 2));
        assertThat(SettingKey.CREW_RUN_END_RULE.parse(SettingKey.CREW_RUN_END_RULE.defaultValue()))
                .isEqualTo(new CrewRunEnd(15));
        assertThat(
                        SettingKey.CREW_RUN_LOBBY_OPEN
                                .validate("{\"lobbyOpenBeforeMinutes\":30}")
                                .valid())
                .isFalse();
    }
}
