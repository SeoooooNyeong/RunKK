package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.RunnerLook;
import com.a207.runkk.avatar.RunnerLookSnapshotStore;
import com.a207.runkk.common.ItemSlot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * avatar.RunnerLookSnapshotStore의 Redis 구현(S15P31A207-300, PM 2026-11-14). 키 ranking:look:{스냅숏}(스냅숏
 * = {종류}:{기간}:{집계 시각 epoch 밀리초}, infra.analysis.RankingCache의 판 번호와 같은 값), 해시 필드 = 회원 ID, 값 = 모습
 * JSON. 만료는 RankingCache.TTL과 같은 25시간이라 다음 0시 스냅숏이 새 키를 쓰면 옛 키는 만료로 사라진다. 읽기는 HMGET 한 번, 쓰기는 HSET과
 * 만료를 Lua 한 번(만료 없는 키가 남지 않는다, [임시:AR-성-90]). Redis를 쓸 수 없으면 비어 있다고 보고(쓰기는 건너뛰고) 경고 로그만 남겨 부르는 쪽이
 * DB에서 읽는다(RankingCache와 같은 규칙, 5.5 장애: Redis). 깨진 값은 그 회원만 없는 것으로 보아 다시 읽어 덮는다.
 */
@Component
public class RedisRunnerLookSnapshotStore implements RunnerLookSnapshotStore {

    /** RankingCache.TTL과 같다(infra.analysis를 import하지 않도록 따로 둔다). */
    static final Duration TTL = Duration.ofHours(25);

    private static final String PREFIX = "ranking:look:";
    private static final Logger log = LoggerFactory.getLogger(RedisRunnerLookSnapshotStore.class);

    /**
     * HSET(필드·값 쌍들)과 PEXPIRE를 Lua 하나로 한다([임시:AR-성-90]). 둘을 따로 부르면 만료만 실패해 만료 없는 키가 남을 수 있다
     * (RedisSelfieFailureStore와 같은 방식). ARGV[1] = 만료 밀리초, 그 뒤 필드·값 쌍.
     */
    private static final DefaultRedisScript<Long> PUT_ALL_WITH_TTL =
            new DefaultRedisScript<>(
                    "for i = 2, #ARGV, 2 do redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1]) end"
                            + " return redis.call('pexpire', KEYS[1], ARGV[1])",
                    Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisRunnerLookSnapshotStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, RunnerLook> find(String snapshot, Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(memberIds);
        List<Object> fields = new ArrayList<>(ids.size());
        ids.forEach(id -> fields.add(String.valueOf(id)));
        List<Object> values;
        try {
            values = redis.opsForHash().multiGet(PREFIX + snapshot, fields);
        } catch (DataAccessException e) {
            log.warn("runner_look_cache_unreadable error={}", e.getClass().getSimpleName());
            return Map.of();
        }
        Map<Long, RunnerLook> looks = new HashMap<>();
        for (int i = 0; i < ids.size() && i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                continue;
            }
            try {
                looks.put(ids.get(i), objectMapper.readValue((String) value, Row.class).toLook());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("runner_look_cache_unreadable error={}", e.getClass().getSimpleName());
            }
        }
        return looks;
    }

    @Override
    public void save(String snapshot, Map<Long, RunnerLook> looks) {
        if (looks.isEmpty()) {
            return;
        }
        try {
            Map<String, String> rows = new HashMap<>();
            for (Map.Entry<Long, RunnerLook> e : looks.entrySet()) {
                rows.put(
                        String.valueOf(e.getKey()),
                        objectMapper.writeValueAsString(Row.of(e.getValue())));
            }
            List<String> args = new ArrayList<>(1 + rows.size() * 2);
            args.add(String.valueOf(TTL.toMillis()));
            rows.forEach(
                    (field, value) -> {
                        args.add(field);
                        args.add(value);
                    });
            redis.execute(PUT_ALL_WITH_TTL, List.of(PREFIX + snapshot), args.toArray());
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("runner_look_cache_unwritable error={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 캐시 값 형식(응답 계약 web.record LookDto와 따로 둔다).
     *
     * @param faceKey 얼굴 키, 없으면 null
     * @param outfit 착용 부위(ItemSlot 이름) → 그림 키
     * @param bodyVariant 3단계 체형
     * @param runAmount 러닝량 0~100
     */
    record Row(String faceKey, Map<ItemSlot, String> outfit, int bodyVariant, int runAmount) {

        static Row of(RunnerLook look) {
            return new Row(look.faceKey(), look.outfit(), look.bodyVariant(), look.runAmount());
        }

        RunnerLook toLook() {
            return new RunnerLook(faceKey, outfit, bodyVariant, runAmount);
        }
    }
}
