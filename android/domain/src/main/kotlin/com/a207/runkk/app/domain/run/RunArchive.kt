package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 측정 결과를 기기 보관함에 넣는다(1차 리뷰 S-1: RunTracker에서 보관 책임을 떼어 낸 협력 객체). RunTracker만 쓴다.
 * - 확정한 기록: 저장하고 전송을 요청한다. 저장 오류면 짧게 두 번 더 시도한다(P-1).
 * - 늦은 심박(FR-043): 기록ID별로 기억해 두었다가 보관함에 덧붙인다. 저장 오류면 두 번 더 시도하고, 그래도 안 되면 다음 늦은
 *   심박이 올 때 함께 다시 넣는다(F-4). 보관함은 머리 정보도 없는 기록의 심박도 늦은 심박 항목(LateHeartRates)으로 받으므로
 *   (D-77) 거부(Rejected)는 업무 규칙 거부뿐이고 다시 해도 같으므로 다시 넣지 않는다.
 * PendingRunRepository.appendHeartRates는 이 클래스만 부른다(design-review.md M32의 "한 곳").
 */
class RunArchive
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val uploadScheduler: UploadScheduler
) {
    private val lock = Any()

    /** 보관함에 아직 넣지 못한 늦은 심박(기록ID별). lock 안에서만 쓴다. */
    private val unsavedLate = mutableMapOf<String, List<HeartRateSample>>()
    private val lateSaving = Mutex()

    /** 저장하고 전송을 요청한다. 세 번 모두 실패하면 거짓(부른 쪽이 안내한다). 반복의 출구는 조건 하나다(P-1). */
    suspend fun save(run: CompletedRun): Boolean {
        var isSaved = pendingRuns.save(run) is Outcome.Success
        var attempt = 1
        while (!isSaved && attempt < SAVE_ATTEMPTS) {
            delay(SAVE_RETRY_MILLIS * attempt)
            isSaved = pendingRuns.save(run) is Outcome.Success
            attempt++
        }
        if (isSaved) uploadScheduler.requestUpload()
        return isSaved
    }

    /** 늦은 심박을 기억해 두고 그 기록의 기억해 둔 심박 전부를 보관함에 덧붙인다. */
    suspend fun appendLateHeartRates(recordId: String, samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        synchronized(lock) { unsavedLate[recordId] = (unsavedLate[recordId].orEmpty() + samples).distinct() }
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
