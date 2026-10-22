package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** OS 위치 권한 포트. 구현 data.device.AndroidLocationPermission. */
interface LocationPermission {
    fun granted(): Boolean
}
