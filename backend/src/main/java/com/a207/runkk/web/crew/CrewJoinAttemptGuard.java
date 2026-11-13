package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.web.attempt.AttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 초대 코드 가입·미리보기의 실패 횟수 제한(D-132, B4 리뷰: 무차별 대입). 회원별 10분에 5번 INVALID_CODE면 그 회원의 코드 가입을 잠시 막는다(429
 * TOO_MANY_ATTEMPTS). crew.MembershipService가 갖던 제한을 표현 계층으로 옮겼다(crew → web.attempt는 계층 역행). 메모리
 * 값이라 재시작하면 비워진다(5.4 인스턴스 1개).
 */
@Component
class CrewJoinAttemptGuard {

    /** 틀린 초대 코드 허용 횟수와 창. */
    static final int INVITE_CODE_FAILURES = 5;

    static final Duration INVITE_CODE_WINDOW = Duration.ofMinutes(10);

    private final AttemptLimiter failures;

    CrewJoinAttemptGuard(Clock clock) {
        this.failures = new AttemptLimiter(INVITE_CODE_FAILURES, INVITE_CODE_WINDOW, clock);
    }

    /**
     * 코드 가입이면 시도 한 번을 먼저 원자적으로 잡는다(tryAcquire, 창 안 5번이면 TOO_MANY_ATTEMPTS). 동시 요청이 확인을 함께 통과해 상한을
     * 넘지 못하게 하려는 것이다(보안 리뷰 MEDIUM). 가입이 INVALID_CODE로 거부되면 잡은 한 번을 실패로 남기고, 성공하면 그 회원의 실패를 지우고, 다른
     * 사유로 끝나면 잡은 한 번을 되돌린다. 크루 ID 가입은 그대로 부른다.
     */
    <T> T guard(long memberId, boolean byCode, Supplier<T> join) {
        if (!byCode) {
            return join.get();
        }
        String key = String.valueOf(memberId);
        if (!failures.tryAcquire(key)) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
        boolean invalidCode = false;
        try {
            T result = join.get();
            failures.reset(key);
            return result;
        } catch (BusinessRuleException e) {
            invalidCode = e.reason() == ReasonCode.INVALID_CODE;
            throw e;
        } finally {
            if (!invalidCode) {
                failures.release(key); // 성공 뒤 reset이면 키가 없어 아무것도 하지 않는다
            }
        }
    }

    /**
     * 초대 코드 미리보기(POST /crews/invite-preview, S15P31A207-295)도 가입과 같은 실패 횟수를 쓴다. 시도 한 번을 먼저 잡고(창 안
     * 5번이면 TOO_MANY_ATTEMPTS) INVALID_CODE면 실패로 남긴다. 맞는 코드여도 실패 횟수를 지우지 않고 잡은 한 번만 되돌린다: 자기가 아는 코드로
     * 횟수를 지운 뒤 다시 맞혀 보는 우회를 막는다(가입 성공만 지운다).
     */
    <T> T preview(long memberId, Supplier<T> lookup) {
        String key = String.valueOf(memberId);
        if (!failures.tryAcquire(key)) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
        boolean invalidCode = false;
        try {
            return lookup.get();
        } catch (BusinessRuleException e) {
            invalidCode = e.reason() == ReasonCode.INVALID_CODE;
            throw e;
        } finally {
            if (!invalidCode) {
                failures.release(key);
            }
        }
    }
}
