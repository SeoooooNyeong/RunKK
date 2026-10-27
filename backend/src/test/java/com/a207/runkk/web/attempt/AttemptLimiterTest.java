package com.a207.runkk.web.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AttemptLimiter 시도 제한")
class AttemptLimiterTest {

    @Test
    @DisplayName("창 안 실패가 상한이면 막고, 창이 지나거나 성공하면 풀린다, 키마다 따로")
    void blocksWithinWindow() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-21T00:00:00Z"));
        Clock clock =
                new Clock() {
                    @Override
                    public ZoneId getZone() {
                        return ZoneId.of("UTC");
                    }

                    @Override
                    public Clock withZone(ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return now.get();
                    }
                };
        AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(10), clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isBlocked("a")).isFalse();
            limiter.recordFailure("a");
        }
        assertThat(limiter.isBlocked("a")).isTrue();
        assertThat(limiter.isBlocked("b")).isFalse();
        now.set(now.get().plus(Duration.ofMinutes(10)));
        assertThat(limiter.isBlocked("a")).isFalse();
        limiter.recordFailure("a");
        limiter.recordFailure("a");
        limiter.recordFailure("a");
        limiter.reset("a");
        assertThat(limiter.isBlocked("a")).isFalse();
    }

    @Test
    @DisplayName("tryAcquire는 확인과 기록을 한 번에 하고 release는 마지막 한 번을 되돌린다")
    void tryAcquireAndRelease() {
        AttemptLimiter limiter =
                new AttemptLimiter(
                        2, Duration.ofHours(1), Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")));
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        limiter.release("ip");
        assertThat(limiter.tryAcquire("ip")).isTrue();
        limiter.release("none");
    }

    @Test
    @DisplayName("ClientAddress: 프록시 뒤라도 IP 글자가 아닌 X-Real-IP는 쓰지 않는다")
    void clientAddress_ignoresGarbageHeader() {
        assertThat(ClientAddress.resolve("172.18.0.5", "203.0.113.9").value())
                .isEqualTo("203.0.113.9");
        assertThat(ClientAddress.resolve("172.18.0.5", "x".repeat(300)).value())
                .isEqualTo("172.18.0.5");
        assertThat(ClientAddress.resolve("8.8.8.8", "203.0.113.9").value()).isEqualTo("8.8.8.8");
    }
}
