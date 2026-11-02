package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 미전송 기록 보관 포트(6.1.14). 구현 data.store.RoomPendingRunRepository.
 * 미전송 기록의 읽기·쓰기는 모두 이 포트를 거친다(ui.home·ui.record도 observePending, 13.2).
 * 저장 오류는 구현이 AppError.Storage로 바꿔 Outcome으로 돌려준다(5.1.2 설계 규칙).
 */
interface PendingRunRepository {
    suspend fun save(run: CompletedRun): Outcome<Unit>

    suspend fun pending(): Outcome<List<CompletedRun>>

    fun observePending(): Flow<List<CompletedRun>>

    suspend fun remove(recordId: String): Outcome<Unit>

    /**
     * 서버가 4xx로 받지 않은 기록을 지우지 않고 사유와 함께 남긴다(uploadState = REJECTED, R12 D2-b). 자동 전송은 이 기록을 다시
     * 보내지 않는다. 기본 구현은 [run]을 상태만 바꿔 다시 저장한다. 보관소 구현은 전송 중 덧붙은 늦은 심박을 덮지 않게 상태 칸만 바꾼다.
     */
    suspend fun markRejected(run: CompletedRun, reason: String): Outcome<Unit> =
        save(run.copy(uploadState = UploadState.REJECTED, rejectedReason = reason))

    /**
     * 서버가 거부해 멈춘 기록(REJECTED)을 모두 미전송(NOT_SENT)으로 되돌리고 거부 사유를 비운다(W40: 앱 결함을 고친 뒤 다시 보내기).
     * 되돌린 건수를 돌려준다. 전송 요청은 부르는 쪽(ResetRejectedRunsUseCase)이 한다. 다시 보내도 거부되면 UploadPendingRunsUseCase가
     * 다시 REJECTED로 남긴다. 기본 구현은 미전송 목록을 읽어 상태만 바꿔 다시 저장한다. 보관소 구현은 상태 칸만 바꾼다(늦은 심박을 덮지 않게).
     */
    suspend fun resetRejected(): Outcome<Int> {
        val rejected =
            when (val read = pending()) {
                is Outcome.Failure -> return read
                is Outcome.Success -> read.value.filter { it.uploadState == UploadState.REJECTED }
            }
        rejected.forEach { run ->
            val saved = save(run.copy(uploadState = UploadState.NOT_SENT, rejectedReason = null))
            if (saved is Outcome.Failure) return saved
        }
        return Outcome.Success(rejected.size)
    }

    suspend fun purgeRoutes(): Outcome<Unit>

    /** 미전송 기록의 심박과 늦은 심박 항목(LateHeartRates)을 함께 지운다(FR-029, TF-VA-31). */
    suspend fun purgeHeartRates(): Outcome<Unit>

    /** 20건 또는 7일을 넘기면 재전송 안내(FR-039). */
    suspend fun needsReminder(now: Instant): Outcome<Boolean>

    /**
     * 늦게 온 심박 덧붙이기. RunTracker만 부른다(design-review.md M32). 머리 정보도 없는 기록이면 거부하지 않고
     * 심박만 담은 항목(LateHeartRates)으로 남긴 뒤 전송을 요청한다(D-77).
     */
    suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit>

    /** 머리 정보 없는 기록의 늦은 심박 항목. UploadPendingRunsUseCase만 읽는다(D-77, D-80). */
    suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>>

    /**
     * 서버로 보낸(또는 서버에 그 기록이 없는) 늦은 심박을 항목에서 뺀다(D-80). sent는 pendingLateHeartRates가 내준 항목이다.
     * 읽은 뒤 같은 기록에 합쳐진 심박은 남기고, 남는 심박이 없을 때만 항목을 지운다(X-1: 전송 중 합쳐진 심박 유실 방지).
     */
    suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit>

    /** 탈퇴 때 늦은 심박 항목까지 전부 지운다. */
    suspend fun clear(): Outcome<Unit>
}
