package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration

/**
 * 워치 표시 정보(FR-042, 3.2.2 DFD 2.4). running이 거짓이면 워치 시작 화면용이고,
 * notice는 시작 요청을 버렸을 때의 안내다. 심박 수집 허용 여부는 data.watch가 설정 사본으로 채운다.
 */
data class WatchDisplay(
    val running: Boolean,
    val elapsed: Duration,
    val distance: Distance,
    val currentPace: Pace?,
    val heartRateBpm: Int?,
    val todayPlan: PlanSnapshot?,
    val notice: String?
)
