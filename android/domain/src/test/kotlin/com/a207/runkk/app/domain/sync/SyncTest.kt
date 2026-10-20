package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> {
        log.add("remote.upload:${run.recordId}")
        return results[run.recordId] ?: Outcome.Success(UploadReceipt(run.recordId, alreadySaved = false))
    }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> {
        log.add("remote.appendHeartRates:$recordId")
        return Outcome.Success(Unit)
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
            listOf("pending.pending", "remote.upload:a", "pending.remove:a", "remote.upload:b", "pending.remove:b"),
            log.calls
        )
        assertEquals(emptyList(), pending.runs)
    }

    @Test
    fun `이미 저장된 기록은 지우고, 늦게 붙은 심박은 따로 보낸다`() = runTest {
        pending.runs += run("a", heartRates = listOf(HeartRateSample(T0, 140)))
        remote.results["a"] = Outcome.Success(UploadReceipt("a", alreadySaved = true))
        val outcomes = UploadPendingRunsUseCase(pending, remote)()
        assertEquals(listOf(UploadOutcome.AlreadySaved("a")), outcomes)
        assertEquals(
            listOf("pending.pending", "remote.upload:a", "pending.remove:a", "remote.appendHeartRates:a"),
            log.calls
        )
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
    fun `보관함을 읽지 못하면 실패 하나를 돌려준다`() = runTest {
        pending.failWith = AppError.Storage
        assertEquals(
            listOf(UploadOutcome.Failed(null, AppError.Storage)),
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
