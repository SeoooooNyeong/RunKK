package com.a207.runkk.app.domain.sync

import java.time.Duration
import javax.inject.Inject

/** 재전송 간격(D-15): 30초 × 2^attempt, 최대 30분. attempt는 0부터 센다. */
class RetryPolicy
@Inject
constructor() {
    fun nextDelay(attempt: Int): Duration {
        require(attempt >= 0) { "attempt는 0 이상: $attempt" }
        if (attempt >= MAX_DOUBLINGS) return MAX_DELAY
        val delay = BASE_DELAY.multipliedBy(1L shl attempt)
        return if (delay > MAX_DELAY) MAX_DELAY else delay
    }

    private companion object {
        val BASE_DELAY: Duration = Duration.ofSeconds(30)
        val MAX_DELAY: Duration = Duration.ofMinutes(30)

        /** 2^6 × 30초 = 32분 > 30분이므로 6번째부터는 상한이다(시프트 넘침 방지). */
        const val MAX_DOUBLINGS = 6
    }
}
