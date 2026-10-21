package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.QuotaCounter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * analysis.QuotaCounter의 Redis 구현(설계 6.1.13 infra.analysis.RedisQuotaCounter, 5.3 LLM 하루 호출 한도).
 * 증가와 첫 만료 설정을 Lua 하나로 해 만료 없는 키가 남지 않는다. 되돌리기는 DECR이다.
 */
@Component
public class RedisQuotaCounter implements QuotaCounter {

    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "local n = redis.call('incr', KEYS[1])"
                            + " if n == 1 then redis.call('pexpire', KEYS[1], ARGV[1]) end"
                            + " return n",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisQuotaCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementAndGet(String key, Duration ttl) {
        Long value = redis.execute(INCREMENT, List.of(key), String.valueOf(ttl.toMillis()));
        if (value == null) {
            throw new IllegalStateException("한도 카운터 응답 없음");
        }
        return value;
    }

    @Override
    public void decrement(String key) {
        redis.opsForValue().decrement(key);
    }
}
