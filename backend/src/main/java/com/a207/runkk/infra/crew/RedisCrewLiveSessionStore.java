package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.CrewLiveSessionStore;
import com.a207.runkk.crew.CrewRunLiveSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * crew.CrewLiveSessionStore의 Redis 구현(설계 5.1.1 infra.crew, D-26). 크루런마다 키 하나(live:crewrun:{id})에 세션
 * 스냅숏 JSON을 두고, 진행 중 크루런 ID는 집합(live:crewruns)에 둔다. 세션은 최대 크루런 시간보다 길게(하루) 살고 Redis는 백업하지 않는다(5.4).
 * 한 크루런의 갱신은 CrewRunLiveService가 게시글 ID 단위로 직렬화한다.
 */
@Component
public class RedisCrewLiveSessionStore implements CrewLiveSessionStore {

    static final String SESSION_PREFIX = "live:crewrun:";
    static final String ACTIVE_KEY = "live:crewruns";

    private static final Duration TIME_TO_LIVE = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCrewLiveSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CrewRunLiveSession> load(long postId) {
        String json = redis.opsForValue().get(key(postId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    CrewRunLiveSession.restore(
                            objectMapper.readValue(json, CrewRunLiveSession.Snapshot.class)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("크루런 세션을 읽지 못했다", e);
        }
    }

    @Override
    public void save(CrewRunLiveSession session) {
        try {
            redis.opsForValue()
                    .set(
                            key(session.postId()),
                            objectMapper.writeValueAsString(session.snapshot()),
                            TIME_TO_LIVE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("크루런 세션을 직렬화하지 못했다", e);
        }
        redis.opsForSet().add(ACTIVE_KEY, String.valueOf(session.postId()));
    }

    @Override
    public void delete(long postId) {
        redis.delete(key(postId));
        redis.opsForSet().remove(ACTIVE_KEY, String.valueOf(postId));
    }

    /** 집합에 남았지만 키가 만료된 ID는 함께 지운다. */
    @Override
    public List<Long> activePostIds() {
        Set<String> ids = redis.opsForSet().members(ACTIVE_KEY);
        if (ids == null) {
            return List.of();
        }
        List<Long> active = ids.stream().map(Long::valueOf).sorted().toList();
        active.stream()
                .filter(id -> !Boolean.TRUE.equals(redis.hasKey(key(id))))
                .forEach(id -> redis.opsForSet().remove(ACTIVE_KEY, String.valueOf(id)));
        return active.stream().filter(id -> Boolean.TRUE.equals(redis.hasKey(key(id)))).toList();
    }

    private static String key(long postId) {
        return SESSION_PREFIX + postId;
    }
}
