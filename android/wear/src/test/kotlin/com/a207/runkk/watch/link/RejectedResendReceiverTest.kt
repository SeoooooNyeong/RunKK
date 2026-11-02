package com.a207.runkk.watch.link

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.ResetRejectedRunsUseCase
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeUploadScheduler
import com.a207.runkk.wear.contract.ResendRejectedMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.MessageEvent
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * W40: 폰의 다시 보내기 요청(RESEND_REJECTED)을 받으면 워치가 자기 거부 기록(REJECTED)을 미전송으로 되돌리고 전송을 깨운다.
 * PhoneMessageService가 우편함을 거치지 않고 [RejectedResendReceiver]로 바로 넘긴다(PhoneLink가 아직 돌지 않아도 처리한다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RejectedResendReceiverTest {
    private val events = mutableListOf<String>()
    private val pending = FakePendingRunRepository(events)
    private val uploads = FakeUploadScheduler()
    private val inbox = WearInbox()

    private fun TestScope.service() = PhoneMessageService().apply {
        inbox = this@RejectedResendReceiverTest.inbox
        resendReceiver = RejectedResendReceiver(ResetRejectedRunsUseCase(pending, uploads), backgroundScope)
    }

    @Test
    fun `W40 다시 보내기 요청을 받으면 REJECTED만 되돌리고 전송을 깨우며 우편함에 넣지 않는다`() = runTest {
        pending.saved +=
            listOf(run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"), run("b"))
        val service = service()

        service.onMessageReceived(message(WearPaths.RESEND_REJECTED, ResendRejectedMessage(1_000).encode()))
        runCurrent()

        assertEquals(listOf(UploadState.NOT_SENT, UploadState.NOT_SENT), pending.saved.map { it.uploadState })
        assertEquals(listOf(null, null), pending.saved.map { it.rejectedReason })
        assertEquals(1, uploads.requests)
        val display = WatchDisplayMessage(isRunning = false, elapsedSeconds = 0, distanceMeters = 0)
        service.onMessageReceived(message(WearPaths.WATCH_DISPLAY, display.encode()))
        assertEquals(WearPaths.WATCH_DISPLAY, inbox.messages.first().path)
    }

    @Test
    fun `W40 되돌릴 기록이 없으면 전송을 깨우지 않는다`() = runTest {
        pending.saved += run("b")

        service().onMessageReceived(message(WearPaths.RESEND_REJECTED, ResendRejectedMessage(1_000).encode()))
        runCurrent()

        assertEquals(listOf("pendingRuns.resetRejected"), events)
        assertEquals(0, uploads.requests)
    }

    @Test
    fun `W40 해독할 수 없는 요청은 버린다`() = runTest {
        pending.saved += run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")

        service().onMessageReceived(message(WearPaths.RESEND_REJECTED, "not json".toByteArray()))
        runCurrent()

        assertEquals(emptyList(), events)
        assertEquals(UploadState.REJECTED, pending.saved.single().uploadState)
    }

    private fun run(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = T
    )

    private fun message(path: String, bytes: ByteArray) = object : MessageEvent {
        override fun getRequestId(): Int = 1

        override fun getPath(): String = path

        override fun getData(): ByteArray = bytes

        override fun getSourceNodeId(): String = "phone"
    }

    private companion object {
        val T: Instant = Instant.parse("2026-11-02T00:00:00Z")
    }
}
