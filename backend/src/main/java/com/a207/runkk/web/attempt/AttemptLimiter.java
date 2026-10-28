package com.a207.runkk.web.attempt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(회원·접속지)별 실패 횟수 창(시도 제한, 단계별 자가 리뷰 B4·B5: 초대 코드 무차별 대입, 관리툴 로그인). 창(window) 안 실패가 max번이면 막고, 창이
 * 지나면 저절로 풀린다. 서버 인스턴스 하나(5.4) 기준의 메모리 값이라 재시작하면 비워진다. 스프링 빈이 아니고 쓰는 쪽이 하나씩 만든다.
 */
public final class AttemptLimiter {

    /** 키가 이만큼 쌓이면 만료된 키를 한 번 치운다(메모리 상한). */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("시도 제한은 1회 이상, 창은 0보다 길어야 한다");
        }
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** 창 안 실패가 상한에 닿았는가. */
    public boolean isBlocked(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            dropExpired(recent, clock.instant());
            return recent.size() >= maxFailures;
        }
    }

    /** 실패 한 번을 남긴다. */
    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (failures.size() >= PRUNE_THRESHOLD) {
            prune(now);
        }
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent, now);
            recent.addLast(now);
        }
    }

    /** 창 안 횟수가 상한 미만이면 한 번을 남기고 참, 상한이면 남기지 않고 거짓. 확인과 기록을 한 번에 해 동시 요청이 상한을 넘지 못한다. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (failures.size() >= PRUNE_THRESHOLD) {
            prune(now);
        }
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent, now);
            if (recent.size() >= maxFailures) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    /** tryAcquire로 남긴 마지막 한 번을 되돌린다(받아들인 요청이 거부로 끝났을 때). */
    public void release(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            recent.pollLast();
        }
    }

    /** 성공하면 그 키의 실패를 지운다. */
    public void reset(String key) {
        failures.remove(key);
    }

    private void dropExpired(Deque<Instant> recent, Instant now) {
        Instant cutoff = now.minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
    }

    private void prune(Instant now) {
        failures.entrySet()
                .removeIf(
                        e -> {
                            synchronized (e.getValue()) {
                                dropExpired(e.getValue(), now);
                                return e.getValue().isEmpty();
                            }
                        });
    }
}
