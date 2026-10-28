package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.SelfieLockStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * avatar.SelfieLockStore의 Redis 구현(설계 5.3: SET NX, 키 회원, 30초). 키 selfie:lock:{회원}, 값은 요청마다 새 토큰이고,
 * 풀 때는 값이 같을 때만 지운다(Lua 한 번).
 */
@Component
public class RedisSelfieLockStore implements SelfieLockStore {

    static final String KEY_PREFIX = "selfie:lock:";

    private static final DefaultRedisScript<Long> UNLOCK =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then"
                            + " return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisSelfieLockStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> tryLock(long memberId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        boolean locked =
                Boolean.TRUE.equals(
                        redis.opsForValue().setIfAbsent(KEY_PREFIX + memberId, token, ttl));
        return locked ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void unlock(long memberId, String token) {
        redis.execute(UNLOCK, List.of(KEY_PREFIX + memberId), token);
    }
}
