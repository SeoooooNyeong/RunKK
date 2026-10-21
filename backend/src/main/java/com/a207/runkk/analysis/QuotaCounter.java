package com.a207.runkk.analysis;

import java.time.Duration;

/**
 * 한도 카운터 포트(설계 6.1.4, D-07). 구현 infra.analysis.RedisQuotaCounter(INCR + 만료를 Lua 하나로). analysis 밖에서
 * 쓰지 않는다(13.1 규칙 4).
 */
public interface QuotaCounter {

    /** 1 증가시킨 값. 키가 처음 생기면 ttl을 건다. */
    long incrementAndGet(String key, Duration ttl);

    /** 1 감소(되돌리기). */
    void decrement(String key);
}
