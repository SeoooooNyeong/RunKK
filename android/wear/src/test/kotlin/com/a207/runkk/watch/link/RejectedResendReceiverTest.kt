package com.a207.runkk.watch.link

import android.net.Uri
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
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemAsset
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

    @Test
    fun `W43 DataClient 항목으로 남긴 다시 보내기 요청도 받아 되돌린다(워치가 늦게 연결돼도)`() = runTest {
        pending.saved += run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")

        service().onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        runCurrent()

        assertEquals(UploadState.NOT_SENT, pending.saved.single().uploadState)
        assertEquals(1, uploads.requests)
    }

    @Test
    fun `W43 같은 요청 시각은 한 번만 처리하고 새 요청 시각은 다시 처리한다`() = runTest {
        pending.saved += run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")
        val service = service()

        service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        service.onMessageReceived(message(WearPaths.RESEND_REJECTED, ResendRejectedMessage(1_000).encode()))
        service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        runCurrent()
        assertEquals(listOf("pendingRuns.resetRejected"), events)

        service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(2_000).encode())))
        runCurrent()
        assertEquals(listOf("pendingRuns.resetRejected", "pendingRuns.resetRejected"), events)
    }

    @Test
    fun `W43 지운 다시 보내기 항목은 무시한다`() = runTest {
        pending.saved += run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")

        service().onDataEvents(listOf(event(DataEvent.TYPE_DELETED, ResendRejectedMessage(1_000).encode())))
        runCurrent()

        assertEquals(emptyList(), events)
    }

    /**
     * TC-5456(경곗값 같음·앞·뒤): 시각 2000을 처리한 뒤 폰 시계가 거꾸로 가 1999가 와도 처리한다(크기가 아니라 같음으로 본다, D-464).
     * 2000은 버리고 2001은 처리한다. 각 경우는 2000을 처리한 수신기에서 따로 본다.
     */
    @Test
    fun `직전 요청 시각보다 앞선 시각도 처리하고 같은 시각만 버린다(TC-5456 D-464)`() = runTest {
        val handled = listOf(1_999L, 2_000L, 2_001L).associateWith { next ->
            events.clear()
            val service = service()
            service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(2_000).encode())))
            service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(next).encode())))
            runCurrent()
            events.count { it == "pendingRuns.resetRejected" }
        }

        assertEquals(mapOf(1_999L to 2, 2_000L to 1, 2_001L to 2), handled)
    }

    /**
     * TC-5458(재시작): 1000을 처리한 뒤 워치 앱이 다시 떠 직전 시각을 잃고 같은 항목 1000을 다시 받으면 다시 처리한다. REJECTED만
     * 되돌리므로 되돌릴 것이 없으면 결과가 같고(전송을 깨우지 않는다), 그 사이 다시 거부된 기록은 되돌린다.
     */
    @Test
    fun `앱이 다시 떠 같은 요청을 다시 받아도 결과가 같고 그 사이 다시 거부된 기록은 되돌린다(TC-5458)`() = runTest {
        pending.saved += run("a").copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")
        service().onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        runCurrent()
        assertEquals(UploadState.NOT_SENT, pending.saved.single().uploadState)
        assertEquals(1, uploads.requests)

        // 다시 뜬 수신기(직전 시각 없음)가 같은 항목을 받는다: 되돌릴 REJECTED가 없다.
        service().onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        runCurrent()
        assertEquals(UploadState.NOT_SENT, pending.saved.single().uploadState)
        assertEquals(1, uploads.requests)

        // 그 사이 서버가 다시 거부했다: 또 다시 뜬 수신기가 같은 항목을 받으면 되돌리고 전송을 깨운다.
        pending.saved[0] = pending.saved[0].copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")
        service().onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, ResendRejectedMessage(1_000).encode())))
        runCurrent()
        assertEquals(UploadState.NOT_SENT, pending.saved.single().uploadState)
        assertEquals(null, pending.saved.single().rejectedReason)
        assertEquals(2, uploads.requests)
        assertEquals(List(3) { "pendingRuns.resetRejected" }, events)
    }

    private fun event(type: Int, bytes: ByteArray): DataEvent {
        val item = object : DataItem {
            override fun getUri(): Uri = Uri.parse("wear://phone${WearPaths.RESEND_REJECTED}")

            override fun getData(): ByteArray = bytes

            override fun getAssets(): Map<String, DataItemAsset> = emptyMap()

            override fun setData(data: ByteArray?): DataItem = this

            override fun isDataValid(): Boolean = true

            override fun freeze(): DataItem = this
        }
        return object : DataEvent {
            override fun getType(): Int = type

            override fun getDataItem(): DataItem = item

            override fun isDataValid(): Boolean = true

            override fun freeze(): DataEvent = this
        }
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
