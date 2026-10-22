package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.LiveBattleSession;
import com.a207.runkk.battle.LiveSessionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * battle.LiveSessionStore의 Redis 구현(설계 5.1.1 infra.battle, 5.3, NFR-004). 대결마다 해시
 * 하나(live:battle:{id})에 대결 값(meta)과 참가자마다 최신 상태(runner:{회원})를 둔다. 진행 중 대결 ID는 집합(live:battles)에 둔다.
 * 세션은 최대 대결 시간보다 길게(하루) 살고 Redis는 백업하지 않는다(5.4).
 */
@Component
@ConditionalOnProperty(name = "runkk.queue", havingValue = "redis", matchIfMissing = true)
public class RedisLiveSessionStore implements LiveSessionStore {

    static final String SESSION_PREFIX = "live:battle:";
    static final String ACTIVE_KEY = "live:battles";

    private static final String META = "meta";
    private static final String RUNNER_PREFIX = "runner:";
    private static final Duration TIME_TO_LIVE = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLiveSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LiveBattleSession> load(long battleId) {
        Map<Object, Object> fields = redis.opsForHash().entries(key(battleId));
        Object meta = fields.get(META);
        if (meta == null) {
            return Optional.empty();
        }
        Meta value = read((String) meta, Meta.class);
        List<LiveBattleSession.Runner> runners =
                fields.entrySet().stream()
                        .filter(e -> ((String) e.getKey()).startsWith(RUNNER_PREFIX))
                        .map(e -> read((String) e.getValue(), LiveBattleSession.Runner.class))
                        .toList();
        return Optional.of(
                LiveBattleSession.restore(
                        new LiveBattleSession.Snapshot(
                                value.battleId(),
                                value.distanceMeters(),
                                value.leaderId(),
                                runners)));
    }

    @Override
    public void save(LiveBattleSession session) {
        LiveBattleSession.Snapshot snapshot = session.snapshot();
        Map<String, String> fields = new HashMap<>();
        fields.put(
                META,
                write(
                        new Meta(
                                snapshot.battleId(),
                                snapshot.distanceMeters(),
                                snapshot.leaderId())));
        snapshot.runners().forEach(r -> fields.put(RUNNER_PREFIX + r.memberId(), write(r)));
        String key = key(snapshot.battleId());
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, TIME_TO_LIVE);
        redis.opsForSet().add(ACTIVE_KEY, String.valueOf(snapshot.battleId()));
    }

    @Override
    public void delete(long battleId) {
        redis.delete(key(battleId));
        redis.opsForSet().remove(ACTIVE_KEY, String.valueOf(battleId));
    }

    @Override
    public List<Long> activeBattleIds() {
        Set<String> ids = redis.opsForSet().members(ACTIVE_KEY);
        return ids == null ? List.of() : ids.stream().map(Long::valueOf).sorted().toList();
    }

    private static String key(long battleId) {
        return SESSION_PREFIX + battleId;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대결 세션을 직렬화하지 못했다", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대결 세션을 읽지 못했다", e);
        }
    }

    /**
     * 해시의 대결 값.
     *
     * @param battleId 대결 ID
     * @param distanceMeters 대결 거리
     * @param leaderId 선두 회원
     */
    record Meta(long battleId, long distanceMeters, Long leaderId) {}
}
