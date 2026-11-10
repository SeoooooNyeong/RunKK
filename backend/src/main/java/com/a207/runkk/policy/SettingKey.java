package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;
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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 운영 기준 항목(설계 6.1.9). 자료사전 기준항목 21개와 운영자 화이트리스트(부록 C). 상수마다 값 형식 검사(validate), 형식 있는 값으로
 * 바꾸기(parse), 기본값(srs.md 예시값)을 가진다. 바꾸기 전후 검사(validateTransition)는 기본이 통과이고 SHOP_ITEMS·ITEM_PRICES만
 * 재정의한다(D-44). 값은 각 값 객체 record의 JSON 표현이다. 검사 범위 숫자는 6.1.9 검사 열 이름의 상수로 둔다(1단계 리뷰 L1).
 */
public enum SettingKey {
    MATCH_CONDITION(MatchCondition.class, "{\"tierWindow\":0,\"ghostAfter\":\"PT15S\"}") {
        @Override
        ValidationResult checkRange(Object value) {
            MatchCondition condition = (MatchCondition) value;
            long ghostSeconds = condition.ghostAfter().toSeconds();
            return rule(
                    between(condition.tierWindow(), 0, MAX_TIER_WINDOW)
                            && between(ghostSeconds, MIN_GHOST_SECONDS, MAX_GHOST_SECONDS),
                    "tierWindow 0~2, ghostAfter 5~60초");
        }
    },
    REWARD_RULE(RewardRule.class, "{\"perKm\":10,\"battleWin\":20,\"dailyCap\":200}") {
        @Override
        ValidationResult checkRange(Object value) {
            RewardRule reward = (RewardRule) value;
            return rule(
                    reward.perKm() > 0 && reward.battleWin() > 0 && reward.dailyCap() > 0,
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
            BodyShapeRule shape = (BodyShapeRule) value;
            return rule(
                    shape.bands().size() == BODY_SHAPE_BANDS
                            && shape.bands().get(0) == 0
                            && strictlyAscending(shape.bands())
                            && shape.restDaysBeforeDecline() >= MIN_REST_DAYS,
                    "구간 4개 오름차순(첫 하한 0), 쉼 일수 ≥ 1");
        }
    },
    SKIN_TONE_RULE(
            SkinToneRule.class, "{\"bands\":[\"04:00\",\"07:00\",\"11:00\",\"17:00\",\"21:00\"]}") {
        @Override
        ValidationResult checkRange(Object value) {
            List<LocalTime> bands = ((SkinToneRule) value).bands();
            return rule(
                    bands.size() == SKIN_TONE_BANDS && strictlyAscending(bands),
                    "시간대 5개가 24시간을 빈틈없이 나눔");
        }
    },
    COACHING_RULE(
            CoachingRule.class,
            "{\"ratio\":0.9,\"defaultBpm\":180,\"holdSeconds\":30,\"cooldownSeconds\":60}") {
        @Override
        ValidationResult checkRange(Object value) {
            CoachingRule coaching = (CoachingRule) value;
            return rule(
                    between(coaching.ratio(), MIN_COACHING_RATIO, MAX_COACHING_RATIO)
                            && between(coaching.defaultBpm(), MIN_DEFAULT_BPM, MAX_DEFAULT_BPM),
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
            RunimalRule runimal = (RunimalRule) value;
            return rule(
                    ratio(runimal.companionRatio())
                            && ratio(runimal.shortRatio())
                            && ratio(runimal.sameCourseRatio())
                            && ratio(runimal.habitRatio())
                            && runimal.shortDistance().meters() > 0,
                    "비율 0~1, 기준 거리 > 0");
        }
    },
    COURSE_SIMILARITY(
            CourseSimilarityRule.class,
            "{\"coverage\":0.8,\"radiusMeters\":50,\"compareCount\":30}") {
        @Override
        ValidationResult checkRange(Object value) {
            CourseSimilarityRule course = (CourseSimilarityRule) value;
            return rule(
                    ratio(course.coverage())
                            && course.radiusMeters() > 0
                            && between(course.compareCount(), MIN_COMPARE_COUNT, MAX_COMPARE_COUNT),
                    "coverage 0~1, radius > 0, 비교 수 1~100");
        }
    },
    LANDMARKS(LandmarkTable.class, Defaults.landmarksJson()) {
        @Override
        ValidationResult checkRange(Object value) {
            List<Landmark> landmarks = ((LandmarkTable) value).landmarks();
            return rule(
                    !landmarks.isEmpty()
                            && landmarks.stream().allMatch(SettingKey::isCompleteLandmark)
                            && landmarks.stream().map(Landmark::key).distinct().count()
                                    == landmarks.size()
                            && strictlyAscending(
                                    landmarks.stream().map(Landmark::distance).toList()),
                    "거리 오름차순, 단계가 LandmarkStage, 키는 영문 대문자·숫자·밑줄이고 표 안에서 고유");
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
            VirtualRecordRule virtual = (VirtualRecordRule) value;
            return rule(
                    virtual.openLowScore() > 0
                            && virtual.openHighScore() > 0
                            && virtual.paceOffsets().size() == PACE_OFFSETS
                            && !virtual.distances().isEmpty(),
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
    /**
     * 크루(2차 배포 기능) 공개 여부(FR-223, v0.14 D-247·D-248). 거짓이면 Runimal 동행 축이 크루런을 세지 않고, 크루 알림을 알림함에만
     * 저장하고 푸시하지 않는다(D138). 행이 없으면 기본 false(V2 규칙).
     */
    CREW_RELEASED(Boolean.class, "false") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    /**
     * 아바타 꾸미기(체형·배경, 2차 배포 기능) 공개 여부(D138). 거짓이면 체형 변화·배경 해금 알림을 알림함에만 저장하고 푸시하지 않는다. 행이 없으면 기본
     * false(CREW_RELEASED와 같다).
     */
    AVATAR_RELEASED(Boolean.class, "false") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    /**
     * 런바디 ai 분석 사용(2차 배포 기능, FR-265, 05-19 D-440). 거짓이면 러닝 저장 뒤 분석 작업을 만들지 않고 ai에 제출·상태 조회하지 않으며,
     * 런바디 조회 API는 403 FEATURE_CLOSED다. 행이 없으면 기본 false.
     */
    RUNBODY_ENABLED(Boolean.class, "false") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    COACH_ENABLED(Boolean.class, "false") {
        @Override
        ValidationResult checkRange(Object value) {
            return ValidationResult.ok();
        }
    },
    COACH_DAILY_LIMIT(Integer.class, "20") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule((Integer) value > 0, "양의 정수");
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
            LlmCallLimit limit = (LlmCallLimit) value;
            return rule(
                    limit.perMember() > 0 && between(limit.global(), 1, LLM_GLOBAL_CEILING),
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
            return rule(
                    between(
                            ((CalorieCoefficient) value).kcalPerKm(),
                            MIN_KCAL_PER_KM,
                            MAX_KCAL_PER_KM),
                    "10~200");
        }
    },
    /** 크루런 대기실 개방(분)·출발 전 알림(분)·최소 준비 인원. CrewRunRule의 앞 세 값. */
    CREW_RUN_LOBBY_OPEN(
            CrewRunLobby.class,
            "{\"lobbyOpenBeforeMinutes\":30,\"reminderBeforeMinutes\":10,\"minReady\":2}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(((CrewRunLobby) value).allPositive(), "분 단위 양수, 최소 준비 인원 양수");
        }
    },
    /** 크루런 종료 기준 km당 분. CrewRunRule의 minutesPerKm. */
    CREW_RUN_END_RULE(CrewRunEnd.class, "{\"minutesPerKm\":15}") {
        @Override
        ValidationResult checkRange(Object value) {
            return rule(((CrewRunEnd) value).minutesPerKm() > 0, "분 단위 양수");
        }
    },
    /** 기준항목이 아니지만 같은 운영 기준 파일에 있는 운영자 계정 목록(부록 C). 기본값은 빈 목록(운영자 없음)이다. */
    OPERATOR_WHITELIST(OperatorWhitelist.class, "{\"accounts\":{}}") {
        @Override
        ValidationResult checkRange(Object value) {
            Map<String, Set<String>> accounts = ((OperatorWhitelist) value).accounts();
            return rule(
                    accounts.values().stream().mapToInt(Set::size).sum() > 0,
                    "(provider, accountId) 목록, 1개 이상");
        }
    };

    /** ai 서버 절대 상한 240 ÷ 1.2(D-62). */
    private static final int LLM_GLOBAL_CEILING = 200;

    private static final int MAX_TIER_WINDOW = 2;
    private static final long MIN_GHOST_SECONDS = 5;
    private static final long MAX_GHOST_SECONDS = 60;
    private static final int BODY_SHAPE_BANDS = 4;
    private static final int MIN_REST_DAYS = 1;
    private static final int SKIN_TONE_BANDS = 5;
    private static final double MIN_COACHING_RATIO = 0.5;
    private static final double MAX_COACHING_RATIO = 1.0;
    private static final int MIN_DEFAULT_BPM = 100;
    private static final int MAX_DEFAULT_BPM = 220;
    private static final int MIN_COMPARE_COUNT = 1;
    private static final int MAX_COMPARE_COUNT = 100;
    private static final int PACE_OFFSETS = 5;
    private static final int MIN_KCAL_PER_KM = 10;
    private static final int MAX_KCAL_PER_KM = 200;

    /** 랜드마크 키 형식: 영문 대문자·숫자·밑줄, 1~40자(avatar_background.landmark_key VARCHAR(40), D-107). */
    private static final Pattern LANDMARK_KEY = Pattern.compile("[A-Z0-9_]{1,40}");

    private static final Logger log = LoggerFactory.getLogger(SettingKey.class);

    private final Type valueType;
    private final String defaultValue;

    SettingKey(Type valueType, String defaultValue) {
        this.valueType = valueType;
        this.defaultValue = defaultValue;
    }

    /** 값 형식·범위 검사(FR-174). 형식 오류만 거부 사유로 바꾸고 그 밖의 예외는 그대로 올린다(1단계 리뷰 L6). */
    public ValidationResult validate(String json) {
        if (json == null) {
            return ValidationResult.invalid("값이 비었다");
        }
        Object value;
        try {
            value = parse(json);
        } catch (IllegalArgumentException e) {
            log.debug("setting_invalid key={} cause={}", name(), e.getClass().getSimpleName());
            return ValidationResult.invalid("형식 오류");
        }
        return checkRange(value);
    }

    /** JSON을 형식 있는 값으로 바꾼다. 형식이 맞지 않거나 값 객체가 거부하면 IllegalArgumentException. */
    public Object parse(String json) {
        try {
            Object value =
                    Defaults.MAPPER.readValue(json, Defaults.MAPPER.constructType(valueType));
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

    private static boolean between(long value, long min, long max) {
        return value >= min && value <= max;
    }

    private static boolean between(double value, double min, double max) {
        return value >= min && value <= max;
    }

    private static boolean ratio(double value) {
        return between(value, 0.0, 1.0);
    }

    private static boolean isCompleteLandmark(Landmark landmark) {
        return landmark.key() != null
                && LANDMARK_KEY.matcher(landmark.key()).matches()
                && landmark.stage() != null
                && landmark.name() != null
                && !landmark.name().isBlank()
                && landmark.distance().meters() > 0;
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

    /**
     * 열거 상수 초기화 중에도 쓸 수 있도록 지연 초기화되는 보조(정적 중첩 클래스는 처음 쓸 때 초기화된다). JSON 변환기와 srs.md 3.3.1 기본 랜드마크 표를
     * 갖는다(결정 Q-65·Q-77, 1단계 리뷰 M4).
     */
    private static final class Defaults {

        static final ObjectMapper MAPPER =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                        .build();

        /**
         * 왕복 랜드마크의 거리는 표의 왕복 거리다. 키는 D-107 (1)의 목록이다. 개성은 빼고 춘천(서울시청–춘천시청 대권 74.8km × 2)을 넣었다
         * (D-성-129, 운영 행은 V65).
         */
        static final List<Landmark> LANDMARKS =
                List.of(
                        domestic("INCHEON", "인천", 60),
                        domestic("CHUNCHEON", "춘천", 150),
                        domestic("DAEJEON", "대전", 280),
                        domestic("JEONJU", "전주", 400),
                        domestic("YEOSU", "여수", 650),
                        domestic("BUSAN", "부산", 800),
                        domestic("JEJU", "제주도", 900),
                        oneWay("BEIJING", LandmarkStage.EAST_ASIA, "베이징", 950),
                        oneWay("TOKYO", LandmarkStage.EAST_ASIA, "도쿄", 1_150),
                        oneWay("TAIPEI", LandmarkStage.EAST_ASIA, "타이베이", 1_480),
                        oneWay("HONG_KONG", LandmarkStage.EAST_ASIA, "홍콩", 2_080),
                        oneWay("HANOI", LandmarkStage.EAST_ASIA, "하노이", 2_750),
                        oneWay("BANGKOK", LandmarkStage.EAST_ASIA, "방콕", 3_720),
                        oneWay("KUALA_LUMPUR", LandmarkStage.INTERCONTINENTAL, "쿠알라룸푸르", 4_600),
                        oneWay("HONOLULU", LandmarkStage.INTERCONTINENTAL, "호놀룰루", 7_330),
                        oneWay("SYDNEY", LandmarkStage.INTERCONTINENTAL, "시드니", 8_320),
                        oneWay("LONDON", LandmarkStage.INTERCONTINENTAL, "런던", 8_860),
                        oneWay("PARIS", LandmarkStage.INTERCONTINENTAL, "파리", 8_960),
                        oneWay("LOS_ANGELES", LandmarkStage.INTERCONTINENTAL, "LA", 9_600),
                        oneWay("NEW_YORK", LandmarkStage.INTERCONTINENTAL, "뉴욕", 11_050),
                        oneWay("SANTIAGO", LandmarkStage.INTERCONTINENTAL, "산티아고", 18_300),
                        oneWay("EARTH_LAP", LandmarkStage.INTERCONTINENTAL, "지구 둘레 1바퀴", 40_075),
                        oneWay("EARTH_2_LAPS", LandmarkStage.CREW_TOTAL, "지구 2바퀴", 80_150),
                        oneWay("KM_100K", LandmarkStage.CREW_TOTAL, "10만 km", 100_000),
                        oneWay("KM_200K", LandmarkStage.CREW_TOTAL, "20만 km", 200_000),
                        oneWay("MOON_APPROACH", LandmarkStage.CREW_TOTAL, "달나라 진입", 380_000),
                        oneWay("MOON", LandmarkStage.CREW_TOTAL, "달까지", 384_400),
                        oneWay("KM_500K", LandmarkStage.CREW_TOTAL, "50만 km", 500_000),
                        oneWay("MOON_ROUND_TRIP", LandmarkStage.CREW_TOTAL, "달 왕복", 768_800),
                        oneWay("KM_1M", LandmarkStage.CREW_TOTAL, "100만 km", 1_000_000));

        private Defaults() {}

        static String landmarksJson() {
            try {
                return MAPPER.writeValueAsString(new LandmarkTable(LANDMARKS));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("기본 랜드마크 표를 만들 수 없다", e);
            }
        }

        private static Landmark domestic(String key, String name, long km) {
            return new Landmark(key, LandmarkStage.DOMESTIC, name, Distance.ofKm(km), true);
        }

        private static Landmark oneWay(String key, LandmarkStage stage, String name, long km) {
            return new Landmark(key, stage, name, Distance.ofKm(km), false);
        }
    }
}
