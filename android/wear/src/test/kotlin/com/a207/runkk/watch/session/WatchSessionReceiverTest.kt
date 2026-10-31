package com.a207.runkk.watch.session

import android.content.Context
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.run.LocalWatchGateway
import com.a207.runkk.watch.testing.FakeAccountRepository
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeSessionRepository
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.FakeUploadScheduler
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 3.3 WatchSessionReceiver: 받은 바이트를 해독해 넘기고, 측정 중이면 deferRevoke를 고른다(D-389, SQ-48, F.4.4). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchSessionReceiverTest {
    private val tokens = FakeSessionRepository()

    private class Harness(val receiver: WatchSessionReceiver, val store: WatchSessionStore, val tracker: RunTracker)

    private fun TestScope.harness(): Harness {
        val cache = OpponentRecordCache(
            File(createTempDirectory("receiver-cache").toFile(), "opponent-records.json"),
            FakeBattleRepository(),
            SchedulerClock(testScheduler),
            backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("receiver-test", Context.MODE_PRIVATE)
        val store = WatchSessionStore(
            tokens,
            FakeAccountRepository(),
            FakePendingRunRepository(),
            cache,
            FakeUploadScheduler(),
            prefs,
            backgroundScope
        )
        val settings = FakeSettingsRepository()
        val gateway = LocalWatchGateway(alertPlayer(), settings, SchedulerClock(testScheduler))
        val tracker = runTracker(gateway, settings = settings)
        return Harness(WatchSessionReceiver(store, tracker, backgroundScope), store, tracker)
    }

    private val session = WatchSessionMessage(7, "SOCIAL", "access", "refresh", issuedAtEpochMillis = 1_000)

    @Test
    fun `로그인 정보를 해독해 받는다`() = runTest {
        val h = harness()

        h.receiver.onSession(session.encode())
        runCurrent()

        assertEquals(WatchSession.Active(7, AccountType.LINKED), h.store.state.value)
    }

    @Test
    fun `해독할 수 없는 본문은 버린다`() = runTest {
        val h = harness()

        h.receiver.onSession("{not json".toByteArray())
        h.receiver.onRevoked(byteArrayOf(1, 2, 3))
        runCurrent()

        assertEquals(WatchSession.None, h.store.state.value)
    }

    @Test
    fun `측정 중이 아니면 로그아웃 표시로 바로 지운다`() = runTest {
        val h = harness()
        h.receiver.onSession(session.encode())

        h.receiver.onRevoked(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000).encode())
        runCurrent()

        assertEquals(WatchSession.None, h.store.state.value)
    }

    @Test
    fun `측정 중이면 지움을 미루고 받은 순서대로 처리한다`() = runTest {
        val h = harness()
        h.tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, RunStartSpec.StartDevice.WATCH))

        h.receiver.onSession(session.encode())
        h.receiver.onRevoked(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000).encode())
        runCurrent()

        assertEquals(WatchSession.RevokePending(7), h.store.state.value)
    }
}
