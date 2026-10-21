package com.a207.runkk.analysis;

import java.time.Duration;
import java.util.List;

/**
 * 한도 카운터 포트(설계 6.1.4, 5.3, D-07). 구현 infra.analysis.RedisQuotaCounter(키 여러 개의 증가·검사·되돌리기를 Lua 하나로).
 * analysis 밖에서 쓰지 않는다(13.1 규칙 4).
 */
public interface QuotaCounter {

    /**
     * 모든 키를 앞에서부터 1씩 올리다가 하나라도 상한을 넘으면 그때까지 올린 키를 모두 되돌리고 false. 넘지 않으면 true. 키가 처음 생기면 ttl을 건다.
     * 원자적이라 동시 요청에도 받아들인 수가 상한을 넘지 않는다.
     */
    boolean tryIncrementAll(List<Limit> limits, Duration ttl);

    /**
     * 키 하나와 그 상한.
     *
     * @param key 카운터 키
     * @param max 상한(이 값까지 허용)
     */
    record Limit(String key, long max) {}
}
