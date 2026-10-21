package com.a207.runkk.app.api.run

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.DisposalRemote
import com.a207.runkk.app.domain.sync.RunUploadRemote
import com.a207.runkk.app.domain.sync.UploadReceipt
import javax.inject.Inject

/** 기록 전송 포트(POST /runs, POST /runs/{id}/heart-rates)를 구현한다(6.1.14 domain.sync 포트). */
internal class RunUploadRemoteImpl
@Inject
constructor(private val api: RunApi) : RunUploadRemote {
    override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> =
        ApiErrorMapper.call { api.upload(run.toRequest()).toDomain() }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
        ApiErrorMapper.call { api.appendHeartRates(recordId, LateHeartRatesRequest(samples.map { it.toRequest() })) }
}

/** 파기 지시 포트(GET /me/disposals, POST /me/disposals/{id}/confirm)를 구현한다(FR-026, FR-029). */
internal class DisposalRemoteImpl
@Inject
constructor(private val api: RunApi) : DisposalRemote {
    override suspend fun pending(): Outcome<List<DisposalOrder>> =
        ApiErrorMapper.call { api.disposals().mapNotNull { it.toDomain() } }

    override suspend fun confirm(orderId: Long): Outcome<Unit> = ApiErrorMapper.call { api.confirmDisposal(orderId) }
}
