package com.a207.runkk.analysis;

import com.a207.runkk.policy.LlmCallLimit;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * LLM 하루 호출 한도(설계 6.1.4 LlmCallQuota, 5.3, FR-065, D-58, D-62). 회원 키·전체 키의 증가·검사·되돌리기를
 * QuotaCounter의 Lua 하나로 원자 처리한다(B5 리뷰: 두 키를 따로 올리던 것을 설계 5.3대로). 회원 키를 먼저 보므로 회원 한도로 거부된 요청은 전체 키를
 * 올리지 않는다. 키 TTL은 48시간이고, LLM 호출이 실패해도 차감은 되돌리지 않는다.
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
        return counter.tryIncrementAll(
                List.of(
                        new QuotaCounter.Limit(
                                PREFIX + day + ":member:" + memberId, limit.perMember()),
                        new QuotaCounter.Limit(PREFIX + day + ":global", limit.global())),
                TTL);
    }
}
