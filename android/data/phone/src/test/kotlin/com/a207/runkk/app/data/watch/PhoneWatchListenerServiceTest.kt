package com.a207.runkk.app.data.watch

import android.content.Intent
import android.net.Uri
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.MessageEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 폰 앱 프로세스가 죽어 있어도 워치의 로그인 정보 요청(SESSION_REQUEST)을 받는다(AN12-1, FR-240, FR-242, D-371).
 * 매니페스트의 수신 서비스가 그 경로를 받고, 이미 보낸 회원이어도 새로 발급받아 요청한 워치에 보낸다.
 */
@RunWith(RobolectricTestRunner::class)
class PhoneWatchListenerServiceTest {
    private val transport = RecordingTransport()
    private val accounts = FakeWatchAccounts(memberId = 7)
    private val memory = FakeWatchMemory().apply { memberId = 7 }
    private val settings = FakeWatchSettings(LINKED)

    private fun TestScope.service(): PhoneWatchListenerService {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val publisher = WatchSessionPublisher(
            transport,
            FakeWatchSession(loggedIn = true),
            settings,
            accounts,
            memory,
            Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC),
            scope
        )
        // 앱 프로세스가 막 떠서 발행기를 시작하지 않은 상태와 같다(Application이 start를 부르기 전).
        return PhoneWatchListenerService().apply {
            this.publisher = publisher
            this.scope = scope
            this.watchLink = WatchLink(transport, settings, scope)
        }
    }

    @Test
    fun `워치의 로그인 정보 요청을 받으면 이미 보낸 회원이어도 새로 발급받아 그 워치에 보낸다`() = runTest {
        val service = service()

        service.onMessageReceived(message(WearPaths.SESSION_REQUEST, "watch-1"))

        assertEquals(1, accounts.issued)
        assertEquals("watch-1", transport.of("sendNow", WearPaths.WATCH_SESSION).single().nodeId)
    }

    @Test
    fun `앱이 막 떠서 설정 사본을 아직 읽지 못했으면 읽을 때까지 기다렸다가 보낸다(콜드 스타트)`() = runTest {
        settings.isSettled.value = false
        val service = service()

        service.onMessageReceived(message(WearPaths.SESSION_REQUEST, "watch-1"))
        assertEquals(0, accounts.issued)
        settings.isSettled.value = true

        assertEquals(1, accounts.issued)
        assertEquals("watch-1", transport.of("sendNow", WearPaths.WATCH_SESSION).single().nodeId)
    }

    @Test
    fun `다른 경로의 메시지는 발급하지 않는다(앱 안 WatchLink가 받는다)`() = runTest {
        val service = service()

        service.onMessageReceived(message(WearPaths.WATCH_MEASURING, "watch-1"))

        assertEquals(0, accounts.issued)
    }

    @Test
    fun `매니페스트가 SESSION_REQUEST 메시지를 이 서비스로 보낸다`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED")
            .setData(Uri.parse("wear://watch-1${WearPaths.SESSION_REQUEST}"))
            .setPackage(context.packageName)

        val resolved = context.packageManager.queryIntentServices(intent, 0).map { it.serviceInfo.name }

        assertEquals(listOf(PhoneWatchListenerService::class.java.name), resolved)
    }

    @Test
    fun `S15P31A207-278 워치의 표시 요청을 받으면 그 워치를 닿는 노드로 등록한 뒤 대기 표시를 다시 보낸다(폰 프로세스가 막 깨어남)`() = runTest {
        val service = service()
        transport.events.clear()

        service.onMessageReceived(message(WearPaths.DISPLAY_REQUEST, "watch-1"))

        assertEquals(
            listOf("reach" to "watch-1", "send" to WearPaths.WATCH_DISPLAY),
            transport.events.map {
                it.kind to (it.nodeId ?: it.path)
            }
        )
        assertFalse(
            WatchDisplayMessage.decode(transport.of("send", WearPaths.WATCH_DISPLAY).single().payload!!).isRunning
        )
        assertEquals(0, accounts.issued, "표시 요청으로 로그인 정보를 발급하지 않는다")
    }

    @Test
    fun `S15P31A207-278 매니페스트가 DISPLAY_REQUEST 메시지를 이 서비스로 보낸다(폰 앱이 꺼져 있어도 깨운다)`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED")
            .setData(Uri.parse("wear://watch-1${WearPaths.DISPLAY_REQUEST}"))
            .setPackage(context.packageName)

        val resolved = context.packageManager.queryIntentServices(intent, 0).map { it.serviceInfo.name }

        assertEquals(listOf(PhoneWatchListenerService::class.java.name), resolved)
    }

    private fun message(path: String, node: String) = object : MessageEvent {
        override fun getRequestId(): Int = 1

        override fun getPath(): String = path

        override fun getData(): ByteArray = ByteArray(0)

        override fun getSourceNodeId(): String = node
    }

    private companion object {
        val LINKED = SettingsSnapshot(
            isLocationConsented = true,
            isHeartRateConsented = false,
            isWatchLinked = true,
            isCourseMapShared = false,
            birthYear = 1990,
            guidanceSettings = GuidanceSettings.DEFAULT
        )
    }
}
