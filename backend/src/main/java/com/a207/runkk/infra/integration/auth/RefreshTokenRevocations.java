package com.a207.runkk.infra.integration.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 폐기한(로그아웃했거나 이미 한 번 쓴) 갱신 토큰 ID 목록(코드 리뷰 2차 M6). Redis 키 auth:refresh:revoked:{jti}를 토큰의 남은 수명만큼
 * 두어 재시작해도 폐기가 남는다. 같은 목록을 메모리에도 두어 Redis가 잠시 안 되면 메모리만으로 판단한다(재시작과 Redis 장애가 겹치면 그 사이 폐기분을 잃는다.
 * PostgreSQL 대안은 결정 목록). 토큰 원문은 저장하지 않는다.
 */
@Component
public class RefreshTokenRevocations {

    static final String KEY_PREFIX = "auth:refresh:revoked:";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRevocations.class);
    private static final String MARK = "1";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<String, Instant> memory = new ConcurrentHashMap<>();

    /** redis가 null이면 메모리만 쓴다(단위 시험). */
    public RefreshTokenRevocations(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /** 처음 폐기하면 true, 이미 폐기된 ID면 false. 회전(한 번만 쓰기)에 쓴다. Redis SET NX EX로 원자적이다. */
    boolean claim(String jti, Instant expiresAt) {
        forgetExpired();
        if (memory.putIfAbsent(jti, expiresAt) != null) {
            return false;
        }
        Duration ttl = remaining(expiresAt);
        if (redis == null || ttl.isZero()) {
            return true;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent(KEY_PREFIX + jti, MARK, ttl);
            return !Boolean.FALSE.equals(first);
        } catch (DataAccessException e) {
            log.warn(
                    "refresh_revocation_store_unavailable op=claim error={}",
                    e.getClass().getSimpleName());
            return true;
        }
    }

    /** 폐기한다(로그아웃). 이미 폐기됐어도 그대로다. */
    void revoke(String jti, Instant expiresAt) {
        forgetExpired();
        memory.put(jti, expiresAt);
        Duration ttl = remaining(expiresAt);
        if (redis == null || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + jti, MARK, ttl);
        } catch (DataAccessException e) {
            log.warn(
                    "refresh_revocation_store_unavailable op=revoke error={}",
                    e.getClass().getSimpleName());
        }
    }

    private Duration remaining(Instant expiresAt) {
        Duration left = Duration.between(clock.instant(), expiresAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private void forgetExpired() {
        Instant now = clock.instant();
        memory.values().removeIf(expiry -> !expiry.isAfter(now));
    }
}
