package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.GpsReadiness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 측정 전에 GPS를 미리 켜 둔다(W55). 랜덤 대결 성사 화면이 출발 전 13초 동안 위치를 잡게 한다. 구현 data.device.FusedGpsWarmUp.
 * 받은 위치는 정확도만 보고 버린다: RunTracker에 넘기지 않으므로 기록·경로·거리에 남지 않는다.
 */
interface GpsWarmUp {
    /**
     * [readiness]를 모으는 동안만 위치를 받고, 모으기를 멈추면 끈다. 시작하면 SEARCHING, 위치를 받을 때마다 정확도가
     * [maxAccuracyMeters] 이하면 READY, 넘으면 SEARCHING을 낸다. 위치 권한이 없거나 위치를 켤 수 없으면 아무것도 내지 않고 끝난다.
     */
    fun readiness(maxAccuracyMeters: Double): Flow<GpsReadiness>

    companion object {
        /** 예열하지 않는다. 워치 앱은 화면이 따로 예열한다(W53, watch.ui.GpsWarmUp). */
        val NONE: GpsWarmUp =
            object : GpsWarmUp {
                override fun readiness(maxAccuracyMeters: Double): Flow<GpsReadiness> = emptyFlow()
            }
    }
}
