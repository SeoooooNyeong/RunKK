package com.a207.runkk.web.chat;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.web.attempt.AttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 채팅 보내기 속도 제한(설계 6.1.7a, D-132, FR-208, NFR-025): 회원당 1초에 5건(제안, D-113 상수), 리액션 달기·떼기는 10건을 넘으면
 * 429 TOO_MANY_ATTEMPTS. ChatController가 서비스 호출 전에 부른다(web.auth AuthAttemptGuard와 같은 꼴). 확인과 기록을 한
 * 번에 해 동시 요청도 한도를 넘지 못한다. 메모리 값이라 재시작하면 비워진다(5.4 인스턴스 1개).
 */
@Component
class ChatRateGuard {

    /** 회원당 창 안 보내기 한도. */
    static final int MAX_POSTS = 5;

    /** 회원당 창 안 리액션 달기·떼기 한도(바뀔 때마다 방 안 전원에게 사건이 나가므로 막는다). */
    static final int MAX_REACTIONS = 10;

    /** 창 길이. */
    static final Duration WINDOW = Duration.ofSeconds(1);

    private final AttemptLimiter posts;
    private final AttemptLimiter reactions;

    ChatRateGuard(Clock clock) {
        this.posts = new AttemptLimiter(MAX_POSTS, WINDOW, clock);
        this.reactions = new AttemptLimiter(MAX_REACTIONS, WINDOW, clock);
    }

    /** 보내기 한 건을 받아들인다. 창 안 한도에 닿았으면 TOO_MANY_ATTEMPTS(기록하지 않는다). */
    void admit(long memberId) {
        if (!posts.tryAcquire(String.valueOf(memberId))) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
    }

    /** 리액션 달기·떼기 한 건을 받아들인다. 창 안 한도에 닿았으면 TOO_MANY_ATTEMPTS(기록하지 않는다). */
    void admitReaction(long memberId) {
        if (!reactions.tryAcquire(String.valueOf(memberId))) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
    }
}
