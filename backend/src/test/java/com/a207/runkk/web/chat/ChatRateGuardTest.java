package com.a207.runkk.web.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 채팅 보내기 속도 제한(D-132, FR-208, NFR-025). TC-6694의 시각 경계. */
@DisplayName("web.chat ChatRateGuard")
class ChatRateGuardTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 밀리초 단위로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-22T00:00:00Z");

        void at(long millis) {
            now = Instant.parse("2026-10-22T00:00:00Z").plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("TC-6694 0.0~0.4초 다섯 건 통과, 0.5초 여섯째 429, 1.2초 다시 통과, 다른 회원은 따로 센다")
    void fivePerSecond() {
        MovableClock clock = new MovableClock();
        ChatRateGuard guard = new ChatRateGuard(clock);
        for (int i = 0; i < 5; i++) {
            clock.at(i * 100L);
            assertThatCode(() -> guard.admit(3)).doesNotThrowAnyException();
        }
        clock.at(500);
        assertThatThrownBy(() -> guard.admit(3))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.TOO_MANY_ATTEMPTS));
        assertThatCode(() -> guard.admit(4)).doesNotThrowAnyException();
        clock.at(1_200);
        assertThatCode(() -> guard.admit(3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BE5-B-18 리액션은 회원당 초당 10건, 보내기 한도와 따로 센다")
    void reactionsTenPerSecond() {
        MovableClock clock = new MovableClock();
        ChatRateGuard guard = new ChatRateGuard(clock);
        for (int i = 0; i < ChatRateGuard.MAX_REACTIONS; i++) {
            assertThatCode(() -> guard.admitReaction(3)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> guard.admitReaction(3))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.TOO_MANY_ATTEMPTS));
        assertThatCode(() -> guard.admit(3)).doesNotThrowAnyException();
        assertThatCode(() -> guard.admitReaction(4)).doesNotThrowAnyException();
        clock.at(1_200);
        assertThatCode(() -> guard.admitReaction(3)).doesNotThrowAnyException();
    }
}
