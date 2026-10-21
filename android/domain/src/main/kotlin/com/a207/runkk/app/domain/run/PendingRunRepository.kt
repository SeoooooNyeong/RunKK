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

    /** 서버로 보낸(또는 서버에 그 기록이 없는) 늦은 심박 항목을 지운다(D-80). */
    suspend fun removeLateHeartRates(recordId: String): Outcome<Unit>

    /** 탈퇴 때 늦은 심박 항목까지 전부 지운다. */
    suspend fun clear(): Outcome<Unit>
}
