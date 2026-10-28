package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 포그라운드 측정 서비스 시작·정지 포트. 구현 data.device.MeasurementService. */
interface MeasurementHost {
    /** OS가 포그라운드 시작을 거부하면(Android 12+ 백그라운드 시작 제한 등) Failure. RunTracker가 시작을 되돌린다. */
    fun startForeground(): Outcome<Unit>

    fun stop()
}
