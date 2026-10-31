package com.a207.runkk.watch.link

import android.content.Context
import android.net.Uri
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.run.LocalWatchGateway
import com.a207.runkk.watch.session.WatchSession
import com.a207.runkk.watch.session.WatchSessionReceiver
import com.a207.runkk.watch.session.WatchSessionStore
import com.a207.runkk.watch.testing.FakeAccountRepository
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeSessionRepository
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.FakeUploadScheduler
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemAsset
import com.google.android.gms.wearable.MessageEvent
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 2.2 watch.link의 로그인 경로(D-371, NFR-050): PhoneMessageService가 WATCH_SESSION(MessageClient)과 SESSION_REVOKED(DataClient 항목)를
 * WatchSessionReceiver로 넘기고, 토큰을 실은 메시지는 PhoneLink 우편함에 넣지 않는다. PhoneLink.requestSession은 SESSION_REQUEST를 보낸다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PhoneSessionRoutingTest {
    private val dataLayer = FakeWearDataLayer()

    private class Harness(val service: PhoneMessageService, val store: WatchSessionStore, val inbox: WearInbox)

    private fun TestScope.harness(): Harness {
        val clock = SchedulerClock(testScheduler)
        val cache = OpponentRecordCache(
            File(createTempDirectory("routing-cache").toFile(), "opponent-records.json"),
            FakeBattleRepository(),
            clock,
            backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("routing-test", Context.MODE_PRIVATE)
        val store = WatchSessionStore(
            FakeSessionRepository(),
            FakeAccountRepository(),
            FakePendingRunRepository(),
            cache,
            FakeUploadScheduler(),
            FakeSettingsRepository(),
            prefs,
            backgroundScope
        )
        val settings = FakeSettingsRepository()
        val tracker = runTracker(LocalWatchGateway(alertPlayer(), settings, clock), settings = settings)
        val inbox = WearInbox()
        val service = PhoneMessageService().apply {
            this.inbox = inbox
            sessionReceiver = WatchSessionReceiver(store, tracker, backgroundScope)
        }
        return Harness(service, store, inbox)
    }

    @Test
    fun `로그인 정보는 세션 수신기로만 가고 우편함에 남지 않는다`() = runTest {
        val h = harness()

        h.service.onMessageReceived(message(WearPaths.WATCH_SESSION, session().encode()))
        runCurrent()

        assertEquals(WatchSession.Active(7, AccountType.LINKED), h.store.state.value)
        val display = WatchDisplayMessage(isRunning = false, elapsedSeconds = 0, distanceMeters = 0)
        h.service.onMessageReceived(message(WearPaths.WATCH_DISPLAY, display.encode()))
        assertEquals(WearPaths.WATCH_DISPLAY, h.inbox.messages.first().path)
    }

    @Test
    fun `DataClient의 로그아웃 표시 항목을 세션 수신기로 넘기고 지운 항목은 무시한다`() = runTest {
        val h = harness()
        h.service.onMessageReceived(message(WearPaths.WATCH_SESSION, session().encode()))
        runCurrent()
        val revoked = SessionRevokedMessage(7, revokedAtEpochMillis = 2_000).encode()

        h.service.onDataEvents(listOf(event(DataEvent.TYPE_DELETED, WearPaths.SESSION_REVOKED, revoked)))
        runCurrent()
        assertEquals(WatchSession.Active(7, AccountType.LINKED), h.store.state.value)

        h.service.onDataEvents(listOf(event(DataEvent.TYPE_CHANGED, WearPaths.SESSION_REVOKED, revoked)))
        runCurrent()
        assertEquals(WatchSession.None, h.store.state.value)
    }

    @Test
    fun `requestSession은 SESSION_REQUEST를 보낸다`() = runTest {
        val collector = HeartRateCollector(FakeHeartRateSensor(), FakeSensorPermission(), backgroundScope)
        val link = PhoneLink(dataLayer, collector, alertPlayer(), SchedulerClock(testScheduler), backgroundScope)

        assertTrue(link.requestSession())

        assertEquals(listOf(WearPaths.SESSION_REQUEST), dataLayer.sentMessages.map { it.second })
    }

    private fun session() = WatchSessionMessage(7, "SOCIAL", "access", "refresh", issuedAtEpochMillis = 1_000)

    private fun message(path: String, bytes: ByteArray) = object : MessageEvent {
        override fun getRequestId(): Int = 1

        override fun getPath(): String = path

        override fun getData(): ByteArray = bytes

        override fun getSourceNodeId(): String = "phone"
    }

    private fun event(type: Int, path: String, bytes: ByteArray): DataEvent {
        val item = object : DataItem {
            override fun getUri(): Uri = Uri.parse("wear://phone$path")

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
}
