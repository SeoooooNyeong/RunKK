package com.a207.runkk.app.domain.run

import java.time.Duration
import java.time.Instant

/**
 * 거리에서 빼는 초과 시속 구간(FR-047 v0.10): 이웃 포인트 사이 시속이 [MAX_SEGMENT_SPEED_KMH]를 넘으면 순간이동·차량 이동으로 보고
 * 그 구간 거리를 더하지 않는다. 서버 ValidityRule.STANDARD.maxSpeedKmh와 같은 값이다(android/contract/policy/ValidityRule.txt로
 * 계약 시험이 대조한다). 서버는 같은 구간을 경로로 다시 찾아 거리와 시간을 빼고 유효를 판정한다.
 */
object OverspeedRule {
    const val MAX_SEGMENT_SPEED_KMH = 25.0

    private const val MILLIS_PER_SECOND = 1_000.0
    private const val MPS_TO_KMH = 3.6

    /** [from]→[to] 사이 [meters]가 상한을 넘는 시속인가. 시각 차가 없으면 판정하지 않는다(서버와 같다). 상한과 같으면 넘지 않는다. */
    fun isOverspeed(meters: Double, from: Instant, to: Instant): Boolean {
        val millis = Duration.between(from, to).toMillis()
        if (millis <= 0) return false
        return meters / (millis / MILLIS_PER_SECOND) * MPS_TO_KMH > MAX_SEGMENT_SPEED_KMH
    }
}
