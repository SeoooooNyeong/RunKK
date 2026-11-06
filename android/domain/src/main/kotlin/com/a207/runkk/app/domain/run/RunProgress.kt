package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration

/**
 * 1초마다의 진행 지표(FR-034, A-02): 경과(운동 시간, 정지 제외), 누적 거리, 현재·평균 페이스, 신호 상태, 정지 여부.
 * 현재 페이스는 폰 화면에 보이지 않고 워치 표시가 쓴다(D-263).
 * heartRate는 최근 30초 안의 심박(없으면 null)이고 호흡 안내와 워치 표시가 쓴다. 정지 중에는 마지막으로 받은 심박이다(D-144).
 * exactDistanceMeters는 반올림 전 누적 거리(m)다. 표시는 [distance](미터 반올림)를 쓰고 대결 도착 판정은 이 값을 쓴다:
 * 999.7m가 1,000m로 올라가 도착 처리되면 서버 경로 검증(정확한 거리)에서 닿지 못해 패배가 된다(W54, 대결 3 실기기 시험).
 */
data class RunProgress(
    val elapsed: Duration,
    val distance: Distance,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val signal: SignalState?,
    val heartRate: HeartRateSample?,
    val isPaused: Boolean = false,
    val exactDistanceMeters: Double = distance.meters.toDouble()
) {
    companion object {
        val INITIAL =
            RunProgress(
                elapsed = Duration.ZERO,
                distance = Distance.ZERO,
                currentPace = null,
                averagePace = null,
                signal = null,
                heartRate = null
            )
    }
}
