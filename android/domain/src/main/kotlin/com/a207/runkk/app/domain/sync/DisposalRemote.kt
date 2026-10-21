package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample

/** 파기 지시 포트(GET /me/disposals, POST /me/disposals/{id}/confirm). 구현 api.run.DisposalRemoteImpl. */
interface DisposalRemote {
    suspend fun pending(): Outcome<List<DisposalOrder>>

    suspend fun confirm(orderId: Long): Outcome<Unit>
}
