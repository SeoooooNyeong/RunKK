package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.QuotaCounter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * analysis.QuotaCounter의 Redis 구현(설계 6.1.13 infra.analysis.RedisQuotaCounter, 5.3 LLM 하루 호출 한도). 키
 * 여러 개의 증가·첫 만료 설정·상한 검사·되돌리기를 Lua 하나로 해 동시 요청 사이에 끼어들 틈이 없고 만료 없는 키가 남지 않는다.
 */
@Component
public class RedisQuotaCounter implements QuotaCounter {

    /** KEYS = 카운터 키들, ARGV[1] = ttl 밀리초, ARGV[2..] = 키마다 상한. 넘으면 올린 키를 모두 되돌리고 0. */
    private static final DefaultRedisScript<Long> TRY_INCREMENT_ALL =
            new DefaultRedisScript<>(
                    "for i = 1, #KEYS do"
                            + " local n = redis.call('incr', KEYS[i])"
                            + " if n == 1 then redis.call('pexpire', KEYS[i], ARGV[1]) end"
                            + " if n > tonumber(ARGV[i + 1]) then"
                            + " for j = 1, i do redis.call('decr', KEYS[j]) end"
                            + " return 0 end"
                            + " end"
                            + " return 1",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisQuotaCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryIncrementAll(List<Limit> limits, Duration ttl) {
        List<String> keys = limits.stream().map(Limit::key).toList();
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(ttl.toMillis()));
        limits.forEach(l -> args.add(String.valueOf(l.max())));
        Long accepted = redis.execute(TRY_INCREMENT_ALL, keys, args.toArray());
        if (accepted == null) {
            throw new IllegalStateException("한도 카운터 응답 없음");
        }
        return accepted == 1L;
    }
}
