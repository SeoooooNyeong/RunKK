package com.a207.runkk.policy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 운영 기준값을 형식 있는 값 객체로 내준다(설계 6.1.9). 처음 만들어질 때 저장된 항목을 캐시에 채우고(D-55), 읽을 때 캐시에 없으면 저장소에서 읽어 형식·범위를
 * 검사한 뒤 parse하고, 없거나 읽을 수 없거나 범위 밖이면 srs.md 예시값을 쓴다(1단계 리뷰 L8). 변경 커밋 뒤 invalidate로 무효화한다(D-24). 값은
 * 형식을 알려 주고 꺼내 다운캐스트를 한 곳에만 둔다(L14).
 */
@Component
public class OperationPolicy {

    private static final Logger log = LoggerFactory.getLogger(OperationPolicy.class);

    private final OperationSettingRepository repository;
    private final ConcurrentHashMap<SettingKey, Object> cache = new ConcurrentHashMap<>();

    public OperationPolicy(OperationSettingRepository repository) {
        this.repository = repository;
        for (OperationSetting setting : repository.findAll()) {
            parseStored(setting.key(), setting.value())
                    .ifPresent(value -> cache.put(setting.key(), value));
        }
    }

    public TierBoundary tierBoundary() {
        return valueOf(SettingKey.TIER_BOUNDARY, TierBoundary.class);
    }

    public MatchCondition matchCondition() {
        return valueOf(SettingKey.MATCH_CONDITION, MatchCondition.class);
    }

    public RewardRule rewardRule() {
        return valueOf(SettingKey.REWARD_RULE, RewardRule.class);
    }

    public CoachingRule coachingRule() {
        return valueOf(SettingKey.COACHING_RULE, CoachingRule.class);
    }

    public RunimalRule runimalRule() {
        return valueOf(SettingKey.RUNIMAL_RULE, RunimalRule.class);
    }

    public CourseSimilarityRule courseSimilarityRule() {
        return valueOf(SettingKey.COURSE_SIMILARITY, CourseSimilarityRule.class);
    }

    public LandmarkTable landmarkTable() {
        return valueOf(SettingKey.LANDMARKS, LandmarkTable.class);
    }

    public VirtualRecordRule virtualRecordRule() {
        return valueOf(SettingKey.VIRTUAL_RECORD_RULE, VirtualRecordRule.class);
    }

    /** SHOP_ITEMS 목록에 ITEM_PRICES의 가격을 덮어쓴 상점 목록. */
    public ShopCatalog shopCatalog() {
        ShopCatalog items = valueOf(SettingKey.SHOP_ITEMS, ShopCatalog.class);
        Map<?, ?> prices = valueOf(SettingKey.ITEM_PRICES, Map.class);
        return new ShopCatalog(
                items.items().stream()
                        .map(
                                item -> {
                                    Object price = prices.get(item.id());
                                    return new ShopItem(
                                            item.id(),
                                            item.name(),
                                            item.slot(),
                                            price instanceof Integer p ? p : item.price(),
                                            item.onSale());
                                })
                        .toList());
    }

    public FeatureToggles features() {
        return new FeatureToggles(
                valueOf(SettingKey.SELFIE_ENABLED, Boolean.class),
                valueOf(SettingKey.AI_COMMENT_ENABLED, Boolean.class));
    }

    public SelfieLimit selfieLimit() {
        return valueOf(SettingKey.SELFIE_LIMIT, SelfieLimit.class);
    }

    public LlmCallLimit llmCallLimit() {
        return valueOf(SettingKey.LLM_DAILY_LIMIT, LlmCallLimit.class);
    }

    public SupplementCap supplementCap() {
        return valueOf(SettingKey.SUPPLEMENT_CAP, SupplementCap.class);
    }

    public NotificationRetention notificationRetention() {
        return valueOf(SettingKey.NOTIFICATION_RETENTION, NotificationRetention.class);
    }

    public CalorieCoefficient calorieCoefficient() {
        return valueOf(SettingKey.CALORIE_COEFFICIENT, CalorieCoefficient.class);
    }

    public BodyShapeRule bodyShapeRule() {
        return valueOf(SettingKey.BODY_SHAPE_RULE, BodyShapeRule.class);
    }

    public SkinToneRule skinToneRule() {
        return valueOf(SettingKey.SKIN_TONE_RULE, SkinToneRule.class);
    }

    /** CREW_RUN_LOBBY_OPEN과 CREW_RUN_END_RULE을 합친 크루런 운영값(설계 갱신 요청 U2). */
    public CrewRunRule crewRunRule() {
        CrewRunLobby lobby = valueOf(SettingKey.CREW_RUN_LOBBY_OPEN, CrewRunLobby.class);
        CrewRunEnd end = valueOf(SettingKey.CREW_RUN_END_RULE, CrewRunEnd.class);
        return new CrewRunRule(
                Duration.ofMinutes(lobby.lobbyOpenBeforeMinutes()),
                Duration.ofMinutes(lobby.reminderBeforeMinutes()),
                lobby.minReady(),
                end.minutesPerKm());
    }

    public OperatorWhitelist operatorWhitelist() {
        return valueOf(SettingKey.OPERATOR_WHITELIST, OperatorWhitelist.class);
    }

    /** 변경 커밋 뒤 캐시 무효화(D-24). 다음 읽기에서 저장소 값을 다시 읽는다. */
    public void invalidate(SettingKey key) {
        cache.remove(key);
    }

    private <T> T valueOf(SettingKey key, Class<T> type) {
        return type.cast(cache.computeIfAbsent(key, this::load));
    }

    private Object load(SettingKey key) {
        return repository
                .find(key)
                .flatMap(setting -> parseStored(key, setting.value()))
                .orElseGet(() -> key.parse(key.defaultValue()));
    }

    /** 저장값이 형식·범위 검사(validate)를 통과할 때만 쓴다. 수동 SQL·옛 판 값이 범위 밖이면 기본값으로 간다. */
    private static Optional<Object> parseStored(SettingKey key, String json) {
        if (!key.validate(json).valid()) {
            log.warn("operation_setting_unusable key={} fallback=default", key);
            return Optional.empty();
        }
        return Optional.of(key.parse(json));
    }
}
