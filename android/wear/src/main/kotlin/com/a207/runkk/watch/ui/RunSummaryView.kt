package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.FinishOutcome
import java.time.Duration

/**
 * 결과 화면의 거리·시간·평균 페이스·평균 심박(FR-249). 평균 심박이 없으면 null(빈칸)이다. 저장하지 않은 종료(거리 0)는 [EMPTY]다.
 */
data class RunSummaryView(
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val averageHeartRate: Int?
) {
    companion object {
        val EMPTY = RunSummaryView(Distance.ZERO, Duration.ZERO, averagePace = null, averageHeartRate = null)

        /** 종료 요약에서 만든다. 평균 페이스는 경과 ÷ 거리, 평균 심박은 기록에 든 심박의 평균(반올림)이다. */
        fun of(outcome: FinishOutcome): RunSummaryView {
            val run = (outcome as? FinishOutcome.Saved)?.run ?: return EMPTY
            val pace = run.distance.takeIf { it.meters > 0 }?.let { Pace.of(run.elapsed, it) }
            val heart = run.heartRates.takeIf {
                it.isNotEmpty()
            }?.map { it.bpm }?.average()?.let { Math.round(it).toInt() }
            return RunSummaryView(run.distance, run.elapsed, pace, heart)
        }
    }
}
