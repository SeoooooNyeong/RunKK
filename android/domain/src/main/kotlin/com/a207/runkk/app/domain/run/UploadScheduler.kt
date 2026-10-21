package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 전송 작업 예약 포트. 구현 data.sync.WorkManagerUploadScheduler. */
interface UploadScheduler {
    fun requestUpload()
}
