package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.SelfieFailureStore;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * avatar.SelfieFailureStore의 Redis 구현. 키 selfie:fail:{날짜}:{회원}, 증가와 첫 만료(48시간)를 Lua 하나로 해 만료 없는 키가
 * 남지 않는다.
 */
@Component
public class RedisSelfieFailureStore implements SelfieFailureStore {

    static final Duration TTL = Duration.ofHours(48);
    private static final String PREFIX = "selfie:fail:";
    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "local n = redis.call('incr', KEYS[1])"
                            + " if n == 1 then redis.call('pexpire', KEYS[1], ARGV[1]) end"
                            + " return n",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisSelfieFailureStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int failuresOn(long memberId, LocalDate day) {
        String value = redis.opsForValue().get(key(memberId, day));
        return value == null ? 0 : Integer.parseInt(value);
    }

    @Override
    public void recordFailure(long memberId, LocalDate day) {
        redis.execute(INCREMENT, List.of(key(memberId, day)), String.valueOf(TTL.toMillis()));
    }

    private static String key(long memberId, LocalDate day) {
        return PREFIX + day + ":" + memberId;
    }
}
