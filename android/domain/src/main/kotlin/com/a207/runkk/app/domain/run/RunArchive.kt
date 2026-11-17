package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 측정 결과를 기기 보관함에 넣는다(RunTracker 협력 객체, D-95, CR34-12). RunTracker만 쓰고, suspend 작업은 주입받은
 * applicationScope에서 돌려 부른 쪽이 기다리지 않는다.
 * - 확정한 기록: 세 번까지 저장하고 성공하면 전송을 요청한다. 모두 실패하면 onFailed로 알린다(조용히 버리지 않는다).
 * - 늦은 심박(FR-043): 기록ID별로 기억해 두었다가 보관함에 덧붙인다. 다시 할 만한 실패면 두 번 더 시도하고, 그래도 안 되면 다음 늦은
 *   심박이 올 때 함께 다시 넣는다. 거부(Rejected)는 다시 해도 같으므로 다시 넣지 않는다(D-77).
 * PendingRunRepository.appendHeartRates는 이 클래스만 부른다(design-review.md M32).
 */
class RunArchive
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val uploadScheduler: UploadScheduler,
    private val scope: CoroutineScope,
    /** 내 기록 저장 결과를 알릴 곳(설계 05-32 OwnRunUploadPort). */
    private val ownUploads: OwnRunUploads
) {
    /** 저장 결과를 따로 모으지 않는 조립(시험·워치). */
    constructor(pendingRuns: PendingRunRepository, uploadScheduler: UploadScheduler, scope: CoroutineScope) :
        this(pendingRuns, uploadScheduler, scope, OwnRunUploads())

    private val lock = Any()

    /** 보관함에 아직 넣지 못한 늦은 심박(기록ID별). lock 안에서만 쓴다. */
    private val unsavedLate = mutableMapOf<String, List<HeartRateSample>>()
    private val lateSaving = Mutex()

    /** 저장하고 전송을 요청한다. 세 번 모두 실패하면 [onFailed]를 부른다. 반복의 출구는 조건 하나다(P-1). */
    fun saveInBackground(run: CompletedRun, onFailed: () -> Unit) {
        ownUploads.onSaving(run)
        scope.launch {
            var isSaved = pendingRuns.save(run) is Outcome.Success
            var attempt = 1
            while (!isSaved && attempt < SAVE_ATTEMPTS) {
                delay(SAVE_RETRY_MILLIS * attempt)
                isSaved = pendingRuns.save(run) is Outcome.Success
                attempt++
            }
            if (isSaved) {
                ownUploads.onSaved(run.recordId)
                uploadScheduler.requestUpload()
            } else {
                ownUploads.onSaveFailed(run.recordId)
                onFailed()
            }
        }
    }

    /** 늦은 심박을 기억해 두고 그 기록의 기억해 둔 심박 전부를 보관함에 덧붙인다. */
    fun appendLateHeartRates(recordId: String, samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        synchronized(lock) { unsavedLate[recordId] = (unsavedLate[recordId].orEmpty() + samples).distinct() }
        scope.launch { flushLate(recordId) }
    }

    private suspend fun flushLate(recordId: String) {
        lateSaving.withLock {
            val batch = synchronized(lock) { unsavedLate.remove(recordId) } ?: return
            var result = pendingRuns.appendHeartRates(recordId, batch)
            var attempt = 1
            while (result.isRetryableFailure() && attempt < SAVE_ATTEMPTS) {
                delay(SAVE_RETRY_MILLIS * attempt)
                result = pendingRuns.appendHeartRates(recordId, batch)
                attempt++
            }
            if (result.isRetryableFailure()) {
                synchronized(lock) { unsavedLate[recordId] = (batch + unsavedLate[recordId].orEmpty()).distinct() }
            }
        }
    }

    private fun Outcome<Unit>.isRetryableFailure(): Boolean = this is Outcome.Failure && error !is AppError.Rejected

    private companion object {
        const val SAVE_ATTEMPTS = 3
        const val SAVE_RETRY_MILLIS = 1_000L
    }
}
