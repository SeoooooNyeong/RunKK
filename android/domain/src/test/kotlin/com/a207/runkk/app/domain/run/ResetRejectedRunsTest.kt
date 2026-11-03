package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/** W40: 서버가 거부해 멈춘 기록(REJECTED)을 미전송으로 되돌리고 전송을 깨운다(폰 설정 "다시 보내기", 워치는 폰 요청을 받아). */
class ResetRejectedRunsTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val uploads = FakeUploadScheduler(log)

    @Test
    fun `W40 REJECTED만 NOT_SENT로 되돌리고 사유를 비우고 되돌린 건수를 돌려준 뒤 전송을 깨운다`() = runTest {
        pending.runs += listOf(rejected("a"), run("b"), rejected("c"))

        val result = ResetRejectedRunsUseCase(pending, uploads)()

        assertEquals(Outcome.Success(2), result)
        assertEquals(
            listOf(UploadState.NOT_SENT, UploadState.NOT_SENT, UploadState.NOT_SENT),
            pending.runs.map {
                it.uploadState
            }
        )
        assertEquals(listOf(null, null, null), pending.runs.map { it.rejectedReason })
        assertEquals(listOf("pending.resetRejected", "upload.requestUpload"), log.calls)
    }

    @Test
    fun `W40 되돌릴 기록이 없으면 전송을 깨우지 않는다`() = runTest {
        pending.runs += run("b")

        assertEquals(Outcome.Success(0), ResetRejectedRunsUseCase(pending, uploads)())

        assertEquals(0, uploads.requests)
    }

    @Test
    fun `W40 보관함을 바꾸지 못하면 실패를 돌려주고 전송을 깨우지 않는다`() = runTest {
        pending.runs += rejected("a")
        pending.failWith = AppError.Storage

        assertEquals(Outcome.Failure(AppError.Storage), ResetRejectedRunsUseCase(pending, uploads)())

        assertEquals(0, uploads.requests)
    }

    @Test
    fun `W40 포트 기본 구현은 미전송 목록에서 REJECTED만 상태를 바꿔 다시 저장하고 건수를 돌려준다`() = runTest {
        val store = MapPendingRuns(listOf(rejected("a"), run("b"), rejected("c")))

        assertEquals(Outcome.Success(2), store.resetRejected())

        assertEquals(listOf("a", "c"), store.saved.map { it.recordId })
        assertEquals(setOf(UploadState.NOT_SENT), store.runs.values.map { it.uploadState }.toSet())
        assertEquals(listOf(null, null, null), store.runs.values.map { it.rejectedReason })
    }

    /** 기본 구현만 쓰는 최소 보관소(save는 같은 기록ID를 덮는다). */
    private class MapPendingRuns(initial: List<CompletedRun>) : PendingRunRepository {
        val runs = LinkedHashMap(initial.associateBy { it.recordId })
        val saved = mutableListOf<CompletedRun>()

        override suspend fun save(run: CompletedRun): Outcome<Unit> {
            saved += run
            runs[run.recordId] = run
            return Outcome.Success(Unit)
        }

        override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.values.toList())

        override fun observePending(): Flow<List<CompletedRun>> = flowOf(runs.values.toList())

        override suspend fun remove(recordId: String): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun purgeRoutes(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun purgeHeartRates(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun needsReminder(now: Instant): Outcome<Boolean> = Outcome.Success(false)

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
            Outcome.Success(Unit)

        override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

        override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private fun rejected(id: String) = run(id).copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")

    private fun run(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T0,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = T0
    )
}
