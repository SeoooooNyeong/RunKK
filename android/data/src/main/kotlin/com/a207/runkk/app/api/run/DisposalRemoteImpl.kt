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

/** 파기 지시 포트(GET /me/disposals, POST /me/disposals/{id}/confirm)를 구현한다(FR-026, FR-029). */
internal class DisposalRemoteImpl
@Inject
constructor(private val api: RunApi) : DisposalRemote {
    override suspend fun pending(): Outcome<List<DisposalOrder>> =
        ApiErrorMapper.call { api.disposals().mapNotNull { it.toDomain() } }

    override suspend fun confirm(orderId: Long): Outcome<Unit> = ApiErrorMapper.call { api.confirmDisposal(orderId) }
}
