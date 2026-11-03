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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * W40: 폰 설정의 "다시 보내기"는 폰의 거부 기록을 되돌리고 연결된 워치에도 같은 일을 요청한다.
 * W43: 못 올린 기록 건수는 폰 건수와 워치가 알린 건수의 합이다.
 */
class ResendRejectedRunsUseCaseTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val watchCount = MutableStateFlow(0)
    private val watch =
        object : WatchResendRequester {
            override suspend fun requestResend() {
                log.add("watch.requestResend")
            }

            override fun observeWatchRejectedCount(): Flow<Int> = watchCount
        }
    private val useCase =
        ResendRejectedRunsUseCase(pending, ResetRejectedRunsUseCase(pending, FakeUploadScheduler(log)), watch)

    @Test
    fun `W40 못 올린 기록 건수는 REJECTED만 센다`() = runTest {
        pending.save(run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"))
        pending.save(run("b"))
        pending.save(run("c").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"))

        assertEquals(2, useCase.observeRejectedCount().first())
    }

    @Test
    fun `R80 폰 건수는 보관소의 거부 건수를 쓰고 전체 목록을 읽지 않는다`() = runTest {
        val counting =
            object : PendingRunRepository by pending {
                override fun observePending(): Flow<List<CompletedRun>> = error("전체 목록을 읽지 않는다")

                override fun observeRejectedCount(): Flow<Int> = flowOf(4)
            }
        val useCase =
            ResendRejectedRunsUseCase(counting, ResetRejectedRunsUseCase(counting, FakeUploadScheduler(log)), watch)
        watchCount.value = 1

        assertEquals(5, useCase.observeRejectedCount().first())
    }

    @Test
    fun `W43 못 올린 기록 건수는 폰 건수와 워치 건수를 더한다`() = runTest {
        pending.save(run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"))
        watchCount.value = 2

        assertEquals(3, useCase.observeRejectedCount().first())
    }

    @Test
    fun `W43 폰에 거부 기록이 없어도 워치 건수만큼 보인다`() = runTest {
        pending.save(run("b"))
        watchCount.value = 1

        assertEquals(1, useCase.observeRejectedCount().first())
    }

    @Test
    fun `W40 다시 보내기는 폰 기록을 되돌려 전송을 깨우고 워치에도 요청한다`() = runTest {
        pending.save(run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"))
        log.calls.clear()

        assertEquals(Outcome.Success(1), useCase())

        assertEquals(listOf("pending.resetRejected", "upload.requestUpload", "watch.requestResend"), log.calls)
        assertEquals(0, useCase.observeRejectedCount().first())
    }

    @Test
    fun `W40 폰 보관함을 바꾸지 못해도 워치 요청은 보내고 실패를 돌려준다`() = runTest {
        pending.failWith = AppError.Storage

        assertEquals(Outcome.Failure(AppError.Storage), useCase())

        assertEquals(listOf("pending.resetRejected", "watch.requestResend"), log.calls)
    }

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
