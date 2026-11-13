package com.a207.runkk.app.domain.run

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * 메인·거리 선택 화면에서 GPS를 미리 켜 둔다(W53).
 * [readiness]를 모으는 동안만 예열하고, 모으기를 멈추면 끈다(배터리 NFR-048).
 */
interface WatchGpsSession {
    /** 예열 중 GPS 상태. 예열할 수 없으면(운동 세션이 이미 있음, 위치 권한 없음, 오류) 아무것도 내지 않고 끝난다. 상한 시간이 지나도 끝난다. */
    fun readiness(): Flow<Readiness>

    suspend fun finishMeasurement(at: Instant) = Unit

    enum class Readiness {
        SEARCHING,
        READY
    }
}
