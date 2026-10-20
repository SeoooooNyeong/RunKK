package com.a207.runkk.policy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 운영 기준값을 형식 있는 값 객체로 내준다(설계 6.1.9). 처음 만들어질 때 저장된 항목을 캐시에 채우고(D-55), 읽을 때 캐시에 없으면 저장소에서 읽어 parse,
 * 그래도 없거나 읽을 수 없으면 srs.md 예시값을 쓴다. 변경 커밋 뒤 invalidate로 무효화한다(D-24).
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
        return (TierBoundary) valueOf(SettingKey.TIER_BOUNDARY);
    }

    public MatchCondition matchCondition() {
        return (MatchCondition) valueOf(SettingKey.MATCH_CONDITION);
    }

    public RewardRule rewardRule() {
        return (RewardRule) valueOf(SettingKey.REWARD_RULE);
    }

    public CoachingRule coachingRule() {
        return (CoachingRule) valueOf(SettingKey.COACHING_RULE);
    }

    public RunimalRule runimalRule() {
        return (RunimalRule) valueOf(SettingKey.RUNIMAL_RULE);
    }

    public CourseSimilarityRule courseSimilarityRule() {
        return (CourseSimilarityRule) valueOf(SettingKey.COURSE_SIMILARITY);
    }

    public LandmarkTable landmarkTable() {
        return (LandmarkTable) valueOf(SettingKey.LANDMARKS);
    }

    public VirtualRecordRule virtualRecordRule() {
        return (VirtualRecordRule) valueOf(SettingKey.VIRTUAL_RECORD_RULE);
    }

    /** SHOP_ITEMS 목록에 ITEM_PRICES의 가격을 덮어쓴 상점 목록. */
    @SuppressWarnings("unchecked")
    public ShopCatalog shopCatalog() {
        ShopCatalog items = (ShopCatalog) valueOf(SettingKey.SHOP_ITEMS);
        Map<Long, Integer> prices = (Map<Long, Integer>) valueOf(SettingKey.ITEM_PRICES);
        return new ShopCatalog(
                items.items().stream()
                        .map(
                                item ->
                                        new ShopItem(
                                                item.id(),
                                                item.name(),
                                                item.slot(),
                                                prices.getOrDefault(item.id(), item.price()),
                                                item.onSale()))
                        .toList());
    }

    public FeatureToggles features() {
        return new FeatureToggles(
                (Boolean) valueOf(SettingKey.SELFIE_ENABLED),
                (Boolean) valueOf(SettingKey.AI_COMMENT_ENABLED));
    }

    public SelfieLimit selfieLimit() {
        return (SelfieLimit) valueOf(SettingKey.SELFIE_LIMIT);
    }

    public LlmCallLimit llmCallLimit() {
        return (LlmCallLimit) valueOf(SettingKey.LLM_DAILY_LIMIT);
    }

    public SupplementCap supplementCap() {
        return (SupplementCap) valueOf(SettingKey.SUPPLEMENT_CAP);
    }

    public NotificationRetention notificationRetention() {
        return (NotificationRetention) valueOf(SettingKey.NOTIFICATION_RETENTION);
    }

    public CalorieCoefficient calorieCoefficient() {
        return (CalorieCoefficient) valueOf(SettingKey.CALORIE_COEFFICIENT);
    }

    public BodyShapeRule bodyShapeRule() {
        return (BodyShapeRule) valueOf(SettingKey.BODY_SHAPE_RULE);
    }

    public SkinToneRule skinToneRule() {
        return (SkinToneRule) valueOf(SettingKey.SKIN_TONE_RULE);
    }

    /** CREW_RUN_LOBBY_OPEN과 CREW_RUN_END_RULE을 합친 크루런 운영값. */
    @SuppressWarnings("unchecked")
    public CrewRunRule crewRunRule() {
        Map<String, Integer> lobby = (Map<String, Integer>) valueOf(SettingKey.CREW_RUN_LOBBY_OPEN);
        Map<String, Integer> end = (Map<String, Integer>) valueOf(SettingKey.CREW_RUN_END_RULE);
        return new CrewRunRule(
                Duration.ofMinutes(lobby.get("lobbyOpenBeforeMinutes")),
                Duration.ofMinutes(lobby.get("reminderBeforeMinutes")),
                lobby.get("minReady"),
                end.get("minutesPerKm"));
    }

    public OperatorWhitelist operatorWhitelist() {
        return (OperatorWhitelist) valueOf(SettingKey.OPERATOR_WHITELIST);
    }

    /** 변경 커밋 뒤 캐시 무효화(D-24). 다음 읽기에서 저장소 값을 다시 읽는다. */
    public void invalidate(SettingKey key) {
        cache.remove(key);
    }

    private Object valueOf(SettingKey key) {
        return cache.computeIfAbsent(key, this::load);
    }

    private Object load(SettingKey key) {
        return repository
                .find(key)
                .flatMap(setting -> parseStored(key, setting.value()))
                .orElseGet(() -> key.parse(key.defaultValue()));
    }

    private static Optional<Object> parseStored(SettingKey key, String json) {
        try {
            return Optional.of(key.parse(json));
        } catch (IllegalArgumentException e) {
            log.warn("operation_setting_unreadable key={} fallback=default", key);
            return Optional.empty();
        }
    }
}
