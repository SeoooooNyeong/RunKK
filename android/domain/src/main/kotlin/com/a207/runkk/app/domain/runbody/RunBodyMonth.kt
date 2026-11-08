package com.a207.runkk.app.domain.runbody

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월간 집계(ai MonthlyOut, FR-267). [averages]에는 점수가 있는 축만 들어 있다. [baseline]·[load]는 달과 관계없이 최근 러닝 기준이다.
 */
data class RunBodyMonth(
    val month: YearMonth,
    val sessionCount: Int,
    val averages: Map<RunBodyAxis, Int>,
    val bests: Bests,
    val weeks: List<Week>,
    val nightRuns: Int,
    val dayRuns: Int,
    val baseline: Baseline,
    val load: Load
) {
    /** 이번 달 개인 최고. 없는 항목은 null. [recoveryHeartRate1m]은 1분 회복 심박 변화(bpm, 음수일수록 빠름)다. */
    data class Bests(
        val fastestKm: Duration?,
        val fastest2Km: Duration?,
        val longest: Distance?,
        val recoveryHeartRate1m: Int?
    )

    data class Week(val start: LocalDate, val distance: Distance)

    /** 개인 기준선(최근 최대 10회 평균, 3회부터). [needs]는 남은 러닝 수다. */
    data class Baseline(
        val isAvailable: Boolean,
        val sessionCount: Int,
        val needs: Int,
        val pace: Pace?,
        val averageHeartRate: Int?
    )

    /** 훈련 부하(7일 거리 ÷ 28일 주평균). 준비 중이면 [availableFrom]에 켜지는 날이 있다. */
    data class Load(
        val isAvailable: Boolean,
        val ratio: Double?,
        val level: Level?,
        val acute: Distance,
        val chronicWeekly: Distance,
        val availableFrom: LocalDate?
    ) {
        enum class Level {
            LOW,
            NORMAL,
            HIGH
        }
    }

    companion object {
        /** 기준선을 쓰는 최소 러닝 수(ai baseline 3회). */
        const val BASELINE_RUNS = 3

        /** 훈련 부하를 켜는 날 수(첫 러닝 + 28일). */
        const val LOAD_DAYS = 28
    }
}
