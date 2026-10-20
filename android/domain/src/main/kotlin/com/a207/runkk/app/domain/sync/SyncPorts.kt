package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample

/** 기록 전송 포트(POST /runs, POST /runs/{id}/heart-rates). 구현 api.run.RunUploadRemoteImpl(CompletedRun → RunUploadRequest 매퍼 포함). */
interface RunUploadRemote {
    suspend fun upload(run: CompletedRun): Outcome<UploadReceipt>

    suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit>
}

/** 파기 지시 포트(GET /me/disposals, POST /me/disposals/{id}/confirm). 구현 api.run.DisposalRemoteImpl. */
interface DisposalRemote {
    suspend fun pending(): Outcome<List<DisposalOrder>>

    suspend fun confirm(orderId: Long): Outcome<Unit>
}
