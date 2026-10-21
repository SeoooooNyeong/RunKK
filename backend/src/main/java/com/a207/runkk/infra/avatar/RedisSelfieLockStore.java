package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.SelfieLockStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** avatar.SelfieLockStore의 Redis 구현(설계 5.3: SET NX, 키 회원, 30초). 키 selfie:lock:{회원}. */
@Component
public class RedisSelfieLockStore implements SelfieLockStore {

    static final String KEY_PREFIX = "selfie:lock:";

    private final StringRedisTemplate redis;

    public RedisSelfieLockStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(long memberId, Duration ttl) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(KEY_PREFIX + memberId, "1", ttl));
    }

    @Override
    public void unlock(long memberId) {
        redis.delete(KEY_PREFIX + memberId);
    }
}
