package com.a207.runkk.aiprivacy;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AiPurgeRequestTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Test
    void dailyRetryAndSevenDayBoundary() {
        var request = AiPurgeRequest.of(1, now);
        assertThat(request.claim(now, false)).isNotNull();
        assertThat(request.failed(now)).isFalse();
        assertThat(request.claim(now.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1), false))
                .isNull();
        assertThat(request.claim(now.plusDays(1), false)).isNotNull();
        assertThat(request.failed(now.plusDays(7).minusNanos(1))).isFalse();
        assertThat(request.failed(now.plusDays(7))).isTrue();
        assertThat(request.status()).isEqualTo(PurgeStatus.ALERTED);
        assertThat(request.failed(now.plusDays(8))).isFalse();
        assertThat(request.claim(now.plusDays(9), false)).isNotNull();
    }

    @Test
    void completedRequestIsOnlyRepeatedByRetentionEvent() {
        var request = AiPurgeRequest.of(1, now);
        request.claim(now, false);
        request.succeeded(now);
        assertThat(request.claim(now.plusDays(6), false)).isNull();
        assertThat(request.claim(now.plusDays(7), false)).isNotNull();
        request.succeeded(now.plusDays(7));
        assertThat(request.claim(now.plusDays(8), true)).isNull();
        assertThat(request.attempts()).isEqualTo(2);
    }

    @Test
    void nextMorningBatchDoesNotMissRetryBySeconds() {
        LocalDateTime morning = now.withHour(3).withMinute(5);
        var request = AiPurgeRequest.of(1, morning);
        request.claim(morning, false);
        request.failed(morning.plusSeconds(2));
        assertThat(request.claim(morning.plusDays(1), false)).isNotNull();
    }

    @Test
    void retentionClaimIsRecoverableAfterProcessStops() {
        var request = AiPurgeRequest.of(1, now);
        request.claim(now, false);
        request.succeeded(now);
        String abandoned = request.claim(now.plusDays(7), true);
        assertThat(request.status()).isEqualTo(PurgeStatus.PENDING);
        assertThat(request.claim(now.plusDays(7).plusMinutes(1), false))
                .isNotNull()
                .isNotEqualTo(abandoned);
    }

    @Test
    void expiredLeaseRejectsStaleCompletion() {
        var request = AiPurgeRequest.of(1, now);
        String first = request.claim(now, false);
        assertThat(request.claim(now.plusSeconds(59), true)).isNull();
        String second = request.claim(now.plusMinutes(1), false);
        assertThat(second).isNotNull().isNotEqualTo(first);
        assertThat(request.owns(first)).isFalse();
        assertThat(request.owns(second)).isTrue();
    }
}
