package com.a207.runkk.app.domain.runbody

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.time.Instant

/**
 * 러닝 하나의 분석 결과(ai SessionResultOut schemaVersion 2, FR-266). [axes]에는 다섯 축이 모두 있고, 점수가 없는 축은
 * [AxisScore.score]가 null이다(앱은 0점으로 그리지 않는다). [split]은 코칭 근거의 전반·후반 페이스이고 나누지 못했으면 null이다.
 */
data class RunBodyResult(
    val summary: Summary,
    val axes: Map<RunBodyAxis, AxisScore>,
    val laps: List<Lap>,
    val previous: Previous?,
    val feedback: Feedback,
    val coaching: List<Coaching>,
    val split: Split?
) {
    /** 점수가 있는 축(오각형 순서). */
    val scoredAxes: List<RunBodyAxis> get() = RunBodyAxis.entries.filter { axes[it]?.score != null }

    data class Summary(
        val distance: Distance,
        val active: Duration,
        val pace: Pace?,
        val averageHeartRate: Int?,
        val maxHeartRate: Int?,
        val startedAt: Instant
    )

    /** 축 하나. [label]·[level]은 ai가 정한 문구·등급이다(앱이 임계값을 다시 판단하지 않는다). */
    data class AxisScore(val score: Int?, val label: String, val level: Level) {
        enum class Level {
            GOOD,
            MID,
            WARN,
            NONE
        }
    }

    /** 1km 구간. [isPartial]이면 마지막 남은 구간이고 [km]는 그 길이다. 아니면 [km]는 구간 번호다. */
    data class Lap(val km: Double, val pace: Pace?, val averageHeartRate: Int?, val isPartial: Boolean)

    /** 직전 러닝. */
    data class Previous(val distance: Distance, val pace: Pace?)

    data class Feedback(val headline: String, val good: String, val fix: String, val next: String)

    /** ai 코칭 플래그(CoachingFlag). */
    enum class Coaching {
        WARMUP_MISSING,
        POSITIVE_SPLIT,
        OVERSTRIDE,
        RECOVERY_SLOWER,
        ACWR_HIGH
    }

    data class Split(val firstHalf: Pace, val secondHalf: Pace)
}
