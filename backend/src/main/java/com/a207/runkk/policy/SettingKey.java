package com.a207.runkk.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Type;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 운영 기준 항목(설계 6.1.9). 자료사전 기준항목 21개와 운영자 화이트리스트(부록 C). 상수마다 값 형식 검사(validate), 형식 있는 값으로
 * 바꾸기(parse), 기본값(srs.md 예시값)을 가진다. 바꾸기 전후 검사(validateTransition)는 기본이 통과이고 SHOP_ITEMS·ITEM_PRICES만
 * 재정의한다(D-44). 값은 각 값 객체 record의 JSON 표현이다.
 */
public enum SettingKey {
    MATCH_CONDITION(MatchCondition.class, "{\"tierWindow\":0,\"ghostAfter\":\"PT15S\"}") {
        @Override
        ValidationResult checkRange(Object value) {
            MatchCondition v = (MatchCondition) value;
            long ghostSeconds = v.ghostAfter().toSeconds();
            return rule(
                    between(v.tierWindow(), 0, 2) && ghostSeconds >= 5 && ghostSeconds <= 60,
                    "tierWindow 0~2, ghostAfter 5~60초");
        }
    },
    REWARD_RULE(RewardRule.class, "{\"perKm\":10,\"battleWin\":20,\"dailyCap\":200}") {
        @Override
        ValidationResult checkRange(Object value) {
            RewardRule v = (RewardRule) value;
            return rule(
                    v.perKm() > 0 && v.battleWin() > 0 && v.dailyCap() > 0,
                    "perKm·battleWin·dailyCap > 0");
        }
    },
    /** 아이템 ID → 가격. SHOP_ITEMS의 가격을 덮어쓴다. */
    ITEM_PRICES(new TypeReference<Map<Long, Integer>>() {}.getType(), "{}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(prices(value).values().stream().allMatch(p -> p > 0), "가격 > 0");
        }

        @Override
        public ValidationResult validateTransition(String beforeJson, String afterJson) {
            try {
                Set<Long> before = prices(parse(beforeJson)).keySet();
                Set<Long> after = prices(parse(afterJson)).keySet();
                return rule(after.containsAll(before), "기존 아이템 ID를 지울 수 없다");
            } catch (IllegalArgumentException e) {
                return ValidationResult.invalid("형식 오류");
            }
        }
    },
    SHOP_ITEMS(ShopCatalog.class, "{\"items\":[]}") {
        @Override
        ValidationResult checkRange(Object value) {
            List<ShopItem> items = ((ShopCatalog) value).items();
            Set<Long> ids = new HashSet<>();
            return rule(items.stream().allMatch(item -> ids.add(item.id())), "아이템 ID는 겹치지 않는다");
        }

        @Override
        public ValidationResult validateTransition(String beforeJson, String afterJson) {
            try {
                ShopCatalog before = (ShopCatalog) parse(beforeJson);
                ShopCatalog after = (ShopCatalog) parse(afterJson);
                return rule(after.allowsTransitionFrom(before), "기존 아이템 ID를 지우거나 부위를 바꿀 수 없다");
            } catch (IllegalArgumentException e) {
                return ValidationResult.invalid("형식 오류");
            }
        }
    },
    BODY_SHAPE_RULE(BodyShapeRule.class, "{\"bands\":[0,1,3,5],\"restDaysBeforeDecline\":4}") {
        @Override
        ValidationResult checkRange(Object value) {
            BodyShapeRule v = (BodyShapeRule) value;
            return rule(
                    v.bands().size() == 4
                            && v.bands().get(0) >= 0
                            && strictlyAscending(v.bands())
                            && v.restDaysBeforeDecline() >= 1,
                    "구간 4개 오름차순, 쉼 일수 ≥ 1");
        }
    },
    SKIN_TONE_RULE(
            SkinToneRule.class, "{\"bands\":[\"04:00\",\"07:00\",\"11:00\",\"17:00\",\"21:00\"]}") {
        @Override
        ValidationResult checkRange(Object value) {
            List<LocalTime> bands = ((SkinToneRule) value).bands();
            return rule(bands.size() == 5 && strictlyAscending(bands), "시간대 5개가 24시간을 빈틈없이 나눔");
        }
    },
    COACHING_RULE(
            CoachingRule.class,
            "{\"ratio\":0.9,\"defaultBpm\":180,\"holdSeconds\":30,\"cooldownSeconds\":60}") {
        @Override
        ValidationResult checkRange(Object value) {
            CoachingRule v = (CoachingRule) value;
            return rule(
                    v.ratio() >= 0.5 && v.ratio() <= 1.0 && between(v.defaultBpm(), 100, 220),
                    "ratio 0.5~1.0, 기본 bpm 100~220");
        }
    },
    TIER_BOUNDARY(TierBoundary.class, "{\"lowerBounds\":[30,35,40,45]}") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok(); // 경계 4개 오름차순은 TierBoundary가 지킨다
        }
    },
    RUNIMAL_RULE(
            RunimalRule.class,
            "{\"companionRatio\":0.5,\"shortDistance\":{\"meters\":3000},\"shortRatio\":0.5,"
                    + "\"sameCourseRatio\":0.6,\"habitWindow\":\"PT2H\",\"habitRatio\":0.6}") {
        @Override
        ValidationResult checkRange(Object value) {
            RunimalRule v = (RunimalRule) value;
            return rule(
                    ratio(v.companionRatio())
                            && ratio(v.shortRatio())
                            && ratio(v.sameCourseRatio())
                            && ratio(v.habitRatio())
                            && v.shortDistance().meters() > 0,
                    "비율 0~1, 기준 거리 > 0");
        }
    },
    COURSE_SIMILARITY(
            CourseSimilarityRule.class,
            "{\"coverage\":0.8,\"radiusMeters\":50,\"compareCount\":30}") {
        @Override
        ValidationResult checkRange(Object value) {
            CourseSimilarityRule v = (CourseSimilarityRule) value;
            return rule(
                    ratio(v.coverage())
                            && v.radiusMeters() > 0
                            && between(v.compareCount(), 1, 100),
                    "coverage 0~1, radius > 0, 비교 수 1~100");
        }
    },
    LANDMARKS(LandmarkTable.class, defaultLandmarks()) {
        @Override
        ValidationResult checkRange(Object value) {
            List<Landmark> landmarks = ((LandmarkTable) value).landmarks();
            return rule(
                    !landmarks.isEmpty()
                            && landmarks.stream()
                                    .allMatch(
                                            l ->
                                                    l.stage() != null
                                                            && l.name() != null
                                                            && !l.name().isBlank()
                                                            && l.distance().meters() > 0)
                            && strictlyAscending(
                                    landmarks.stream().map(Landmark::distance).toList()),
                    "거리 오름차순, 단계가 LandmarkStage");
        }
    },
    VIRTUAL_RECORD_RULE(
            VirtualRecordRule.class,
            "{\"openLowScore\":27,\"openHighScore\":48,"
                    + "\"paceOffsets\":[-0.05,-0.025,0,0.025,0.05],"
                    + "\"distances\":[{\"meters\":1000},{\"meters\":3000},{\"meters\":5000},"
                    + "{\"meters\":10000}]}") {
        @Override
        ValidationResult checkRange(Object value) {
            VirtualRecordRule v = (VirtualRecordRule) value;
            return rule(
                    v.openLowScore() > 0
                            && v.openHighScore() > 0
                            && v.paceOffsets().size() == 5
                            && !v.distances().isEmpty(),
                    "열린 구간 점수 > 0, 오프셋 5개");
        }
    },
    SELFIE_ENABLED(Boolean.class, "true") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    AI_COMMENT_ENABLED(Boolean.class, "true") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    SELFIE_LIMIT(SelfieLimit.class, "{\"perDay\":1}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(((SelfieLimit) value).perDay() > 0, "양의 정수");
        }
    },
    LLM_DAILY_LIMIT(LlmCallLimit.class, "{\"perMember\":1,\"global\":200}") {
        @Override
        ValidationResult checkRange(Object value) {
            LlmCallLimit v = (LlmCallLimit) value;
            return rule(
                    v.perMember() > 0 && v.global() > 0 && v.global() <= LLM_GLOBAL_CEILING,
                    "양의 정수 2개, 전체 ≤ 200(ai 서버 절대 상한 240 ÷ 1.2, D-62)");
        }
    },
    SUPPLEMENT_CAP(SupplementCap.class, "{\"cap\":{\"meters\":10000}}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(((SupplementCap) value).cap().meters() > 0, "양의 거리");
        }
    },
    NOTIFICATION_RETENTION(NotificationRetention.class, "{\"days\":30}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(((NotificationRetention) value).days() > 0, "양의 정수(일)");
        }
    },
    CALORIE_COEFFICIENT(CalorieCoefficient.class, "{\"kcalPerKm\":65}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(between(((CalorieCoefficient) value).kcalPerKm(), 10, 200), "10~200");
        }
    },
    /** 크루런 대기실 개방(분)·출발 전 알림(분)·최소 준비 인원. CrewRunRule의 앞 세 값. */
    CREW_RUN_LOBBY_OPEN(
            new TypeReference<Map<String, Integer>>() {}.getType(),
            "{\"lobbyOpenBeforeMinutes\":30,\"reminderBeforeMinutes\":10,\"minReady\":2}") {
        @Override
        ValidationResult checkRange(Object value) {
            return positiveMinutes(
                    value, Set.of("lobbyOpenBeforeMinutes", "reminderBeforeMinutes", "minReady"));
        }
    },
    /** 크루런 종료 기준 km당 분. CrewRunRule의 minutesPerKm. */
    CREW_RUN_END_RULE(
            new TypeReference<Map<String, Integer>>() {}.getType(), "{\"minutesPerKm\":15}") {
        @Override
        ValidationResult checkRange(Object value) {
            return positiveMinutes(value, Set.of("minutesPerKm"));
        }
    },
    /** 기준항목이 아니지만 같은 운영 기준 파일에 있는 운영자 계정 목록(부록 C). 기본값은 빈 목록(운영자 없음)이다. */
    OPERATOR_WHITELIST(OperatorWhitelist.class, "{\"accounts\":{}}") {
        @Override
        ValidationResult checkRange(Object value) {
            Map<String, Set<String>> accounts = ((OperatorWhitelist) value).accounts();
            return rule(
                    accounts.values().stream().mapToInt(Set::size).sum() >= 1,
                    "(provider, accountId) 목록, 1개 이상");
        }
    };

    /** ai 서버 절대 상한 240 ÷ 1.2(D-62). */
    private static final int LLM_GLOBAL_CEILING = 200;

    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .build();

    private final Type valueType;
    private final String defaultValue;

    SettingKey(Type valueType, String defaultValue) {
        this.valueType = valueType;
        this.defaultValue = defaultValue;
    }

    /** 값 형식·범위 검사(FR-174). */
    public ValidationResult validate(String json) {
        if (json == null) {
            return ValidationResult.invalid("값이 비었다");
        }
        try {
            return checkRange(parse(json));
        } catch (RuntimeException e) {
            return ValidationResult.invalid("형식 오류");
        }
    }

    /** JSON을 형식 있는 값으로 바꾼다. 형식이 맞지 않으면 IllegalArgumentException. */
    public Object parse(String json) {
        try {
            Object value = MAPPER.readValue(json, MAPPER.constructType(valueType));
            if (value == null) {
                throw new IllegalArgumentException("값이 비었다: " + name());
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("형식 오류: " + name(), e);
        }
    }

    /** 바꾸기 전후 검사. 기본은 통과(D-44). */
    public ValidationResult validateTransition(String beforeJson, String afterJson) {
        return ValidationResult.ok();
    }

    /** srs.md 예시값. 저장된 값이 없을 때 쓴다. */
    public String defaultValue() {
        return defaultValue;
    }

    /** 상수별 범위 검사(6.1.9 표의 검사 열). */
    abstract ValidationResult checkRange(Object value);

    private static ValidationResult rule(boolean satisfied, String rule) {
        return satisfied ? ValidationResult.ok() : ValidationResult.invalid(rule);
    }

    private static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static boolean ratio(double value) {
        return value >= 0 && value <= 1;
    }

    private static <T extends Comparable<? super T>> boolean strictlyAscending(List<T> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i).compareTo(values.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer> prices(Object value) {
        return (Map<Long, Integer>) value;
    }

    @SuppressWarnings("unchecked")
    private static ValidationResult positiveMinutes(Object value, Set<String> keys) {
        Map<String, Integer> v = (Map<String, Integer>) value;
        return rule(
                v.keySet().equals(keys) && v.values().stream().allMatch(m -> m != null && m > 0),
                "분 단위 양수 " + keys);
    }

    /** srs.md 3.3.1 랜드마크 목록(결정 Q-65·Q-77). 왕복 랜드마크의 거리는 표의 왕복 거리다. */
    private static String defaultLandmarks() {
        Object[][] rows = {
            {"DOMESTIC", "인천", 60, true},
            {"DOMESTIC", "개성", 120, true},
            {"DOMESTIC", "대전", 280, true},
            {"DOMESTIC", "전주", 400, true},
            {"DOMESTIC", "여수", 650, true},
            {"DOMESTIC", "부산", 800, true},
            {"DOMESTIC", "제주도", 900, true},
            {"EAST_ASIA", "베이징", 950, false},
            {"EAST_ASIA", "도쿄", 1_150, false},
            {"EAST_ASIA", "타이베이", 1_480, false},
            {"EAST_ASIA", "홍콩", 2_080, false},
            {"EAST_ASIA", "하노이", 2_750, false},
            {"EAST_ASIA", "방콕", 3_720, false},
            {"INTERCONTINENTAL", "쿠알라룸푸르", 4_600, false},
            {"INTERCONTINENTAL", "호놀룰루", 7_330, false},
            {"INTERCONTINENTAL", "시드니", 8_320, false},
            {"INTERCONTINENTAL", "런던", 8_860, false},
            {"INTERCONTINENTAL", "파리", 8_960, false},
            {"INTERCONTINENTAL", "LA", 9_600, false},
            {"INTERCONTINENTAL", "뉴욕", 11_050, false},
            {"INTERCONTINENTAL", "산티아고", 18_300, false},
            {"INTERCONTINENTAL", "지구 둘레 1바퀴", 40_075, false},
            {"CREW_TOTAL", "지구 2바퀴", 80_150, false},
            {"CREW_TOTAL", "10만 km", 100_000, false},
            {"CREW_TOTAL", "20만 km", 200_000, false},
            {"CREW_TOTAL", "달나라 진입", 380_000, false},
            {"CREW_TOTAL", "달까지", 384_400, false},
            {"CREW_TOTAL", "50만 km", 500_000, false},
            {"CREW_TOTAL", "달 왕복", 768_800, false},
            {"CREW_TOTAL", "100만 km", 1_000_000, false}
        };
        StringBuilder json = new StringBuilder("{\"landmarks\":[");
        for (int i = 0; i < rows.length; i++) {
            Object[] row = rows[i];
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"stage\":\"")
                    .append(row[0])
                    .append("\",\"name\":\"")
                    .append(row[1])
                    .append("\",\"distance\":{\"meters\":")
                    .append(((Integer) row[2]).longValue() * 1000)
                    .append("},\"roundTrip\":")
                    .append(row[3])
                    .append('}');
        }
        return json.append("]}").toString();
    }
}
