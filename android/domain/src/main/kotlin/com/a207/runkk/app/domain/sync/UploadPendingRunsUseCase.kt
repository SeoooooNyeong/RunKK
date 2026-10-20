package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.PendingRunRepository
import javax.inject.Inject

/**
 * 보관 기록을 서버로 보낸다(6.1.14 domain.sync). data.sync.UploadWorker가 부르고,
 * 실패가 있으면 RetryPolicy 간격 뒤 다시 실행된다(D-15).
 * 성공·"이미 저장됨"이면 보관함에서 지운다. "이미 저장됨"인 기록에 심박이 있으면 늦게 붙은 심박을 따로 보낸다(FR-043).
 */
class UploadPendingRunsUseCase
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val remote: RunUploadRemote
) {
    suspend operator fun invoke(): List<UploadOutcome> {
        val runs =
            when (val pending = pendingRuns.pending()) {
                is Outcome.Failure -> return listOf(UploadOutcome.Failed(null, pending.error))
                is Outcome.Success -> pending.value
            }
        return runs.map { run ->
            when (val uploaded = remote.upload(run)) {
                is Outcome.Failure -> UploadOutcome.Failed(run.recordId, uploaded.error)

                is Outcome.Success -> {
                    val removed = pendingRuns.remove(run.recordId)
                    val receipt = uploaded.value
                    if (receipt.alreadySaved && run.heartRates.isNotEmpty()) {
                        remote.appendHeartRates(run.recordId, run.heartRates)
                    }
                    when {
                        removed is Outcome.Failure -> UploadOutcome.Failed(run.recordId, removed.error)
                        receipt.alreadySaved -> UploadOutcome.AlreadySaved(run.recordId)
                        else -> UploadOutcome.Sent(run.recordId)
                    }
                }
            }
        }
    }
}
