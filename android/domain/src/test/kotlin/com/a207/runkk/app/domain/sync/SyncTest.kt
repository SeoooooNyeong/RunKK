package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PauseInterval
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private fun run(id: String, heartRates: List<HeartRateSample> = emptyList()) = CompletedRun(
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
    heartRates = heartRates,
    savedAt = T0
)

private class FakeRunUploadRemote(private val log: CallLog) : RunUploadRemote {
    val results = mutableMapOf<String, Outcome<UploadReceipt>>()

    /** upload가 받은 기록(전송 직전 자르기 확인용). */
    val uploaded = mutableListOf<CompletedRun>()

    override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> {
        log.add("remote.upload:${run.recordId}")
        uploaded += run
        return results[run.recordId] ?: Outcome.Success(UploadReceipt(run.recordId, isAlreadySaved = false))
    }

    var appendResult: Outcome<Unit> = Outcome.Success(Unit)
    val appendResults = mutableMapOf<String, Outcome<Unit>>()
    val appended = mutableListOf<Pair<String, List<HeartRateSample>>>()

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> {
        log.add("remote.appendHeartRates:$recordId")
        appended += recordId to samples
        return appendResults[recordId] ?: appendResult
    }
}

private class FakeDisposalRemote(
    private val log: CallLog,
    var orders: Outcome<List<DisposalOrder>> = Outcome.Success(emptyList())
) : DisposalRemote {
    override suspend fun pending(): Outcome<List<DisposalOrder>> {
        log.add("disposal.pending")
        return orders
    }

    override suspend fun confirm(orderId: Long): Outcome<Unit> {
        log.add("disposal.confirm:$orderId")
        return Outcome.Success(Unit)
    }
}

/** UploadPendingRunsUseCase·SyncDisposalsUseCase·RetryPolicy(6.1.14 domain.sync, D-15, D-54). */
class SyncTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val remote = FakeRunUploadRemote(log)
    private val disposal = FakeDisposalRemote(log)

    @Test
    fun `보관 기록마다 전송하고 성공하면 지운다`() = runTest {
        pending.runs += listOf(run("a"), run("b"))
        val outcomes = UploadPendingRunsUseCase(pending, remote)()
        assertEquals(listOf(UploadOutcome.Sent("a"), UploadOutcome.Sent("b")), outcomes)
        assertEquals(
            listOf(
                "pending.pending",
                "remote.upload:a",
                "pending.remove:a",
                "remote.upload:b",
                "pending.remove:b",
                "pending.pendingLateHeartRates"
            ),
            log.calls
        )
        assertEquals(emptyList(), pending.runs)
    }

    @Test
    fun `이미 저장된 기록은 지우고, 늦게 붙은 심박은 따로 보낸다`() = runTest {
        pending.runs += run("a", heartRates = listOf(HeartRateSample(T0, 140)))
        remote.results["a"] = Outcome.Success(UploadReceipt("a", isAlreadySaved = true))
        val outcomes = UploadPendingRunsUseCase(pending, remote)()
        assertEquals(listOf(UploadOutcome.AlreadySaved("a")), outcomes)
        assertEquals(
            listOf(
                "pending.pending",
                "remote.upload:a",
                "remote.appendHeartRates:a",
                "pending.remove:a",
                "pending.pendingLateHeartRates"
            ),
            log.calls
        )
    }

    @Test
    fun `늦게 붙은 심박을 보내지 못하면 보관함에 남긴다`() = runTest {
        pending.runs += run("a", heartRates = listOf(HeartRateSample(T0, 140)))
        remote.results["a"] = Outcome.Success(UploadReceipt("a", isAlreadySaved = true))
        remote.appendResult = Outcome.Failure(AppError.Network)
        val outcomes = UploadPendingRunsUseCase(pending, remote)()
        assertEquals(listOf(UploadOutcome.Failed("a", AppError.Network)), outcomes)
        assertEquals(listOf("a"), pending.runs.map { it.recordId })
    }

    @Test
    fun `전송에 실패한 기록은 남기고 다음 기록을 계속 보낸다`() = runTest {
        pending.runs += listOf(run("a"), run("b"))
        remote.results["a"] = Outcome.Failure(AppError.Network)
        val outcomes = UploadPendingRunsUseCase(pending, remote)()
        assertEquals(listOf(UploadOutcome.Failed("a", AppError.Network), UploadOutcome.Sent("b")), outcomes)
        assertEquals(listOf("a"), pending.runs.map { it.recordId })
    }

    @Test
    fun `R12 D2-b 서버가 4xx로 거부한 기록은 지우지 않고 사유와 함께 REJECTED로 남기고 다음 실행에 다시 보내지 않는다`() = runTest {
        pending.runs += listOf(run("bad"), run("ok"))
        remote.results["bad"] = Outcome.Failure(AppError.Rejected("BAD_REQUEST"))

        val first = UploadPendingRunsUseCase(pending, remote)()

        assertEquals(
            listOf(UploadOutcome.Failed("bad", AppError.Rejected("BAD_REQUEST")), UploadOutcome.Sent("ok")),
            first
        )
        val kept = pending.runs.single()
        assertEquals("bad", kept.recordId)
        assertEquals(UploadState.REJECTED, kept.uploadState)
        assertEquals("BAD_REQUEST", kept.rejectedReason)

        log.calls.clear()
        val second = UploadPendingRunsUseCase(pending, remote)()

        assertEquals(emptyList(), second)
        assertEquals(listOf("pending.pending", "pending.pendingLateHeartRates"), log.calls)
        assertEquals(listOf("bad"), pending.runs.map { it.recordId })
    }

    @Test
    fun `R12 D2-b 네트워크·인증·5xx 실패는 REJECTED로 바꾸지 않고 다음 실행에 다시 보낸다`() = runTest {
        pending.runs += listOf(run("net"), run("auth"), run("server"))
        remote.results["net"] = Outcome.Failure(AppError.Network)
        remote.results["auth"] = Outcome.Failure(AppError.Unauthorized)
        remote.results["server"] = Outcome.Failure(AppError.Server(503))

        UploadPendingRunsUseCase(pending, remote)()

        assertTrue(pending.runs.all { it.uploadState == UploadState.NOT_SENT })
        assertTrue(log.calls.none { it.startsWith("pending.markRejected") })
        remote.results.clear()
        UploadPendingRunsUseCase(pending, remote)()
        assertEquals(emptyList(), pending.runs)
    }

    @Test
    fun `R12 D2-b 거부 표시를 남기지 못하면 저장 실패로 돌려 재전송 대상이 된다`() = runTest {
        pending.runs += run("bad")
        remote.results["bad"] = Outcome.Failure(AppError.Rejected("BAD_REQUEST"))
        val failing =
            object : PendingRunRepository by pending {
                override suspend fun markRejected(run: CompletedRun, reason: String): Outcome<Unit> =
                    Outcome.Failure(AppError.Storage)
            }

        val outcomes = UploadPendingRunsUseCase(failing, remote)()

        assertEquals(listOf(UploadOutcome.Failed("bad", AppError.Storage)), outcomes)
    }

    @Test
    fun `R12 D2 정지 끝이 기록 끝을 넘친 옛 기록은 보내기 직전에 잘라 보내고 성공하면 지운다`() = runTest {
        // 에뮬레이터 보관함 사례: 끝 = 시작 + 110_191ms, 둘째 정지 끝 = 시작 + 110_192ms.
        val overflowing =
            run("old").copy(
                elapsed = Duration.ofMillis(70_090),
                pauses =
                    listOf(
                        PauseInterval(T0.plusMillis(40_000), T0.plusMillis(60_000)),
                        PauseInterval(T0.plusMillis(90_091), T0.plusMillis(110_192))
                    )
            )
        pending.runs += overflowing

        val outcomes = UploadPendingRunsUseCase(pending, remote)()

        assertEquals(listOf(UploadOutcome.Sent("old")), outcomes)
        val sent = remote.uploaded.single()
        assertEquals(T0.plusMillis(110_191), sent.endedAt)
        assertEquals(T0.plusMillis(110_191), sent.pauses.last().to)
        assertEquals(emptyList(), pending.runs)
    }

    @Test
    fun `보관함을 읽지 못하면 실패 하나를 돌려주고 늦은 심박 항목도 보내지 않는다`() = runTest {
        pending.failWith = AppError.Storage
        pending.lateEntries += LateHeartRates("x", listOf(HeartRateSample(T0, 120)))
        assertEquals(
            listOf(UploadOutcome.Failed(null, AppError.Storage)),
            UploadPendingRunsUseCase(pending, remote)()
        )
        assertEquals(listOf("pending.pending"), log.calls)
    }

    @Test
    fun `M-1 머리 정보 없는 기록의 늦은 심박 항목을 기록 뒤에 보내고, 보내면 지우며 결과는 더하지 않는다(D-80)`() = runTest {
        val samples = listOf(HeartRateSample(T0, 120))
        pending.runs += run("a")
        pending.lateEntries += LateHeartRates("x", samples)

        val outcomes = UploadPendingRunsUseCase(pending, remote)()

        assertEquals(listOf(UploadOutcome.Sent("a")), outcomes)
        assertEquals(listOf("x" to samples), remote.appended)
        assertEquals(
            listOf(
                "pending.pending",
                "remote.upload:a",
                "pending.remove:a",
                "pending.pendingLateHeartRates",
                "remote.appendHeartRates:x",
                "pending.removeLateHeartRates:x"
            ),
            log.calls
        )
        assertEquals(emptyList(), pending.lateEntries)
    }

    @Test
    fun `X-1 늦은 심박 항목은 보낸 내용을 넘겨 지운다(전송 중 합쳐진 심박은 보관소가 남긴다)`() = runTest {
        val entry = LateHeartRates("x", listOf(HeartRateSample(T0, 120)))
        pending.lateEntries += entry

        UploadPendingRunsUseCase(pending, remote)()

        assertEquals(listOf(entry), pending.lateRemoved)
    }

    @Test
    fun `늦은 심박 항목 - 서버에 그 기록이 없으면(RECORD_NOT_FOUND) 지운다`() = runTest {
        pending.lateEntries += LateHeartRates("x", listOf(HeartRateSample(T0, 120)))
        remote.appendResult = Outcome.Failure(AppError.Rejected(UploadPendingRunsUseCase.RECORD_NOT_FOUND))

        assertEquals(emptyList(), UploadPendingRunsUseCase(pending, remote)())
        assertEquals(emptyList(), pending.lateEntries)
    }

    @Test
    fun `늦은 심박 항목 - 일시 오류와 다른 거부는 남기고 실패를 더한다(NFR-032)`() = runTest {
        pending.lateEntries +=
            listOf(
                LateHeartRates("net", listOf(HeartRateSample(T0, 120))),
                LateHeartRates("auth", listOf(HeartRateSample(T0, 121))),
                LateHeartRates("deny", listOf(HeartRateSample(T0, 122))),
                LateHeartRates("ok", listOf(HeartRateSample(T0, 123)))
            )
        remote.appendResults["net"] = Outcome.Failure(AppError.Network)
        remote.appendResults["auth"] = Outcome.Failure(AppError.Unauthorized)
        remote.appendResults["deny"] = Outcome.Failure(AppError.Rejected("FORBIDDEN"))

        val outcomes = UploadPendingRunsUseCase(pending, remote)()

        assertEquals(
            listOf(
                UploadOutcome.Failed("net", AppError.Network),
                UploadOutcome.Failed("auth", AppError.Unauthorized),
                UploadOutcome.Failed("deny", AppError.Rejected("FORBIDDEN"))
            ),
            outcomes
        )
        assertEquals(listOf("net", "auth", "deny"), pending.lateEntries.map { it.recordId })
    }

    @Test
    fun `늦은 심박 항목 - 보낸 뒤 지우지 못하면 실패를 더한다`() = runTest {
        pending.lateEntries += LateHeartRates("x", listOf(HeartRateSample(T0, 120)))
        pending.lateRemoveFailWith = AppError.Storage

        assertEquals(listOf(UploadOutcome.Failed("x", AppError.Storage)), UploadPendingRunsUseCase(pending, remote)())
    }

    @Test
    fun `늦은 심박 항목 목록을 읽지 못하면 기록 결과 뒤에 실패 하나를 붙인다`() = runTest {
        pending.runs += run("a")
        pending.lateReadFailWith = AppError.Storage

        assertEquals(
            listOf(UploadOutcome.Sent("a"), UploadOutcome.Failed(null, AppError.Storage)),
            UploadPendingRunsUseCase(pending, remote)()
        )
    }

    @Test
    fun `파기 지시 - 종류별로 지운 뒤 확인을 보낸다`() = runTest {
        disposal.orders =
            Outcome.Success(
                listOf(DisposalOrder(1, DisposalOrder.Kind.ROUTE), DisposalOrder(2, DisposalOrder.Kind.HEART_RATE))
            )
        assertEquals(Outcome.Success(Unit), SyncDisposalsUseCase(pending, disposal)())
        assertEquals(
            listOf(
                "disposal.pending",
                "pending.purgeRoutes",
                "disposal.confirm:1",
                "pending.purgeHeartRates",
                "disposal.confirm:2"
            ),
            log.calls
        )
    }

    @Test
    fun `파기 지시 - 기기에서 지우지 못하면 확인을 보내지 않는다`() = runTest {
        disposal.orders = Outcome.Success(listOf(DisposalOrder(1, DisposalOrder.Kind.ROUTE)))
        pending.failWith = AppError.Storage
        assertEquals(Outcome.Failure(AppError.Storage), SyncDisposalsUseCase(pending, disposal)())
        assertEquals(listOf("disposal.pending", "pending.purgeRoutes"), log.calls)
    }

    @Test
    fun `파기 지시 - 목록을 받지 못하면 실패를 그대로 돌려준다`() = runTest {
        disposal.orders = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Failure(AppError.Network), SyncDisposalsUseCase(pending, disposal)())
    }

    @Test
    fun `재전송 간격 - 30초 × 2^attempt, 최대 30분 (D-15)`() {
        val policy = RetryPolicy()
        assertEquals(Duration.ofSeconds(30), policy.nextDelay(0))
        assertEquals(Duration.ofMinutes(1), policy.nextDelay(1))
        assertEquals(Duration.ofMinutes(2), policy.nextDelay(2))
        assertEquals(Duration.ofMinutes(16), policy.nextDelay(5))
        assertEquals(Duration.ofMinutes(30), policy.nextDelay(6))
        assertEquals(Duration.ofMinutes(30), policy.nextDelay(1_000))
        assertFailsWith<IllegalArgumentException> { policy.nextDelay(-1) }
    }
}
