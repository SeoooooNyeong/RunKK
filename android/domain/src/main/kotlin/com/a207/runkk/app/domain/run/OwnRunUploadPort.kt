package com.a207.runkk.app.domain.run

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 내 기록의 저장·업로드 상태 공개 포트(설계 05-32 OwnRunUploadPort, domain.run). domain.crew가 domain.sync·data를 보지 않고 내
 * recordId 하나만 관찰한다. 이 실행에서 본 결과가 우선이고, 없으면 보관함(미전송·거부)으로 판단한다. HTTP를 새로 부르지 않는다.
 */
class OwnRunUploadPort
@Inject
constructor(
    private val uploads: OwnRunUploads,
    private val pendingRuns: PendingRunRepository,
    private val uploadScheduler: UploadScheduler
) {
    fun observe(recordId: String): Flow<OwnUploadState> =
        combine(uploads.states, pendingRuns.observePending()) { states, pending ->
            val known = states[recordId]
            val boxed = pending.firstOrNull { it.recordId == recordId }
            when {
                known != null && known != OwnUploadState.Pending -> known
                boxed?.uploadState == UploadState.REJECTED -> OwnUploadState.Rejected(boxed.rejectedReason.orEmpty())
                boxed != null || known == OwnUploadState.Pending -> OwnUploadState.Pending
                else -> OwnUploadState.Unknown
            }
        }.distinctUntilChanged()

    /** 내 기록을 다시 보낸다(기존 전송 예약). 실패 표시는 다음 전송 결과가 바꾼다. */
    fun retry(recordId: String) {
        uploads.onRetry(recordId)
        uploadScheduler.requestUpload()
    }
}
