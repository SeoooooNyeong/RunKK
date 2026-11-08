package com.a207.runkk.app.domain.runbody

import com.a207.runkk.app.core.model.Pace

/**
 * 요약 "최근 10회 변화"(G-14, 서버 RunBodyTrendResponse, 계약 v1.2 8절). 유효 러닝을 10회씩 묶은 회차에서 마지막으로 다 찬 회차가
 * [recent], 그 앞이 [previous]다. [round]가 0이면 아직 10회 전이고 [needs]가 남은 횟수다.
 *
 * @property sameHeartRatePaceChange 같은 심박에서의 페이스 변화(초/km, 양수면 빨라짐). 셈할 수 없으면 null
 * @property runimalType 나의 러닝 유형(서버 RunimalType 이름). 없으면 null
 */
data class RunBodyTrend(
    val validRunCount: Int,
    val round: Int,
    val needs: Int,
    val recent: Window?,
    val previous: Window?,
    val sameHeartRatePaceChange: Int?,
    val runimalType: String?
) {
    /** 한 회차(10회): 순번 범위, 거리 가중 평균 페이스, 주간·월간(30일) 빈도, 평균 심박(없으면 null). */
    data class Window(
        val fromOrdinal: Int,
        val toOrdinal: Int,
        val pace: Pace,
        val runsPerWeek: Double,
        val runsPerMonth: Double,
        val averageHeartRate: Int?
    )

    /** 직전 10회 대비 평균 페이스 변화(초/km, 양수면 빨라짐). 직전 회차가 없으면 null. */
    val paceChange: Int?
        get() {
            val before = previous ?: return null
            val now = recent ?: return null
            return before.pace.secondsPerKm - now.pace.secondsPerKm
        }
}
