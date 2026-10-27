package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration

/**
 * 워치 표시 정보(FR-042, 3.2.2 DFD 2.4). isRunning이 거짓이면 워치 시작 화면용이고, notice는 안내 키다(시작 요청을 버렸을 때,
 * 정지 자동 종료 예고·종료. 문구는 data.watch가 리소스로 바꾼다, D-111). 심박 수집 허용 여부는 data.watch가 설정 사본으로 채운다.
 * isPausable은 자유런 측정 중일 때만 참이고(spec.mode.isPausable, D-137) isPaused는 정지 중이다(FR-194, F.4.2).
 */
data class WatchDisplay(
    val isRunning: Boolean,
    val elapsed: Duration,
    val distance: Distance,
    val currentPace: Pace?,
    val heartRateBpm: Int?,
    val todayPlan: PlanSnapshot?,
    val notice: WatchNotice?,
    val isPaused: Boolean = false,
    val isPausable: Boolean = false
)
