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

/**
 * 기록 전송 포트(POST /runs, POST /runs/{id}/heart-rates)를 구현한다(6.1.14 domain.sync 포트). 기록 전송은 업로드 전용 클라이언트
 * ([RunUploadApi], 본문 gzip, Jira 327)로, 늦은 심박은 일반 클라이언트([RunApi])로 보낸다.
 */
internal class RunUploadRemoteImpl
@Inject
constructor(private val api: RunApi, private val uploads: RunUploadApi) :
    RunUploadRemote {
    override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> =
        ApiErrorMapper.call { uploads.upload(run.toFittedRequest()).toDomain() }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
        ApiErrorMapper.call { api.appendHeartRates(recordId, LateHeartRatesRequest(samples.map { it.toRequest() })) }
}
