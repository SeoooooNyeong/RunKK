package com.a207.runkk.analysis;

import com.a207.runkk.policy.LlmCallLimit;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * LLM 하루 호출 한도(설계 6.1.4 LlmCallQuota, FR-065, D-58, D-62). 회원 키를 먼저 올려 넘으면 회원 키만 되돌리고, 다음에 전체 키를 올려
 * 넘으면 둘 다 되돌린다. 각 증가는 원자적(INCR)이라 동시 요청에도 받아들인 수가 한도를 넘지 않는다. 회원 키를 먼저 보므로 회원 한도로 거부된 요청이 전체 키를
 * 잠깐이라도 올리지 않는다. 키 TTL은 48시간이고, LLM 호출이 실패해도 차감은 되돌리지 않는다.
 */
@Component
public class LlmCallQuota {

    static final Duration TTL = Duration.ofHours(48);
    private static final String PREFIX = "runkk:llm-quota:";

    private final QuotaCounter counter;

    public LlmCallQuota(QuotaCounter counter) {
        this.counter = counter;
    }

    public boolean tryAcquire(long memberId, LocalDate day, LlmCallLimit limit) {
        String memberKey = PREFIX + day + ":member:" + memberId;
        if (counter.incrementAndGet(memberKey, TTL) > limit.perMember()) {
            counter.decrement(memberKey);
            return false;
        }
        String globalKey = PREFIX + day + ":global";
        if (counter.incrementAndGet(globalKey, TTL) > limit.global()) {
            counter.decrement(globalKey);
            counter.decrement(memberKey);
            return false;
        }
        return true;
    }
}
