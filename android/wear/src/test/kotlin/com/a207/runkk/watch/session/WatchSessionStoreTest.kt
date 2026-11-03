package com.a207.runkk.watch.session

import android.content.Context
import android.content.SharedPreferences
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.testing.FakeAccountRepository
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeSessionRepository
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.FakeUploadScheduler
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.selfTrack
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 3.3 WatchSessionStore와 상태도 2.4.26(FR-240~FR-242, D-372, D-389, D-390). 숨은 의존 H-55(옛 로그아웃 표시 버림)와
 * H-56(다른 회원 로그인 때 보관함 → 상대 기록 → 토큰 순 삭제)을 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchSessionStoreTest {
    private val events = mutableListOf<String>()
    private val tokens = FakeSessionRepository(events)
    private val accounts = FakeAccountRepository(events)
    private val pendingRuns = FakePendingRunRepository(events)
    private val uploads = FakeUploadScheduler()
    private val settings = FakeSettingsRepository()
    private val battles = FakeBattleRepository()
    private val prefs: SharedPreferences =
        RuntimeEnvironment.getApplication().getSharedPreferences("watch-session-test", Context.MODE_PRIVATE)
    private val cacheDir: File = createTempDirectory("session-cache").toFile()

    private lateinit var cache: OpponentRecordCache

    private fun TestScope.store(): WatchSessionStore {
        cache = OpponentRecordCache(
            File(cacheDir, "opponent-records.json"),
            battles,
            SchedulerClock(testScheduler),
            backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        val describe = { "(cache=${cache.selfCandidates().size})" }
        pendingRuns.describeOnClear = describe
        tokens.describeOnClear = describe
        return WatchSessionStore(tokens, accounts, pendingRuns, cache, uploads, settings, prefs, backgroundScope)
    }

    private fun session(memberId: Long, issuedAt: Long, accountType: String = "SOCIAL") =
        WatchSessionMessage(memberId, accountType, "access-$memberId", "refresh-$memberId", issuedAt)

    @Test
    fun `로그인 정보를 받으면 토큰을 두고 Active가 되며 미전송 기록 전송을 깨운다`() = runTest {
        val store = store()

        assertEquals(Outcome.Success(Unit), store.accept(session(7, issuedAt = 1_000)))

        assertEquals(WatchSession.Active(7, AccountType.LINKED), store.state.value)
        assertEquals(SessionTokens("access-7", "refresh-7"), tokens.tokens)
        assertEquals(1, uploads.requests)
        store.accept(session(7, issuedAt = 2_000, accountType = "GUEST"))
        assertEquals(WatchSession.Active(7, AccountType.GUEST), store.state.value)
    }

    @Test
    fun `H-56 다른 회원의 로그인 정보가 오면 보관함 → 상대 기록 → 토큰 순으로 지운 뒤 새 토큰을 둔다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack())
        battles.tracks[RecordBattleKind.CELEBRITY] = emptyList()
        cache.refresh(memberId = 7)
        events.clear()

        store.accept(session(8, issuedAt = 2_000))

        assertEquals(listOf("pendingRuns.clear(cache=1)", "tokens.clear(cache=0)", "tokens.save"), events)
        assertEquals(WatchSession.Active(8, AccountType.LINKED), store.state.value)
    }

    @Test
    fun `H-56 이전 회원 보관함을 지우지 못하면 새 회원 로그인 정보를 받지 않는다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        pendingRuns.isClearFailing = true

        assertIs<Outcome.Failure>(store.accept(session(8, issuedAt = 2_000)))

        assertEquals(WatchSession.Active(7, AccountType.LINKED), store.state.value)
        assertEquals(SessionTokens("access-7", "refresh-7"), tokens.tokens)
    }

    @Test
    fun `같은 회원이 로그아웃 뒤 다시 오면 미전송 기록을 지우지 않는다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        store.revoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000))
        events.clear()

        store.accept(session(7, issuedAt = 3_000))

        assertEquals(listOf("tokens.save"), events)
    }

    @Test
    fun `H-55 새 세션보다 이른 로그아웃 표시는 revoke·deferRevoke 모두 버린다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 5_000))
        events.clear()

        store.revoke(SessionRevokedMessage(7, revokedAtEpochMillis = 4_999))
        store.deferRevoke(SessionRevokedMessage(7, revokedAtEpochMillis = 4_000))

        assertEquals(emptyList(), events)
        assertEquals(WatchSession.Active(7, AccountType.LINKED), store.state.value)
    }

    @Test
    fun `H-55 프로세스를 다시 열어도 받아 둔 issuedAt으로 옛 로그아웃 표시를 버린다`() = runTest {
        store().accept(session(7, issuedAt = 5_000))
        events.clear()

        val reopened = store()
        assertEquals(WatchSession.Active(7, AccountType.LINKED), reopened.state.value)
        reopened.revoke(SessionRevokedMessage(7, revokedAtEpochMillis = 4_000))

        assertEquals(emptyList(), events)
    }

    @Test
    fun `로그아웃 표시를 받으면 서버 로그아웃 → 상대 기록 → 토큰 순으로 지우고 미전송 기록은 남긴다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack())
        battles.tracks[RecordBattleKind.CELEBRITY] = emptyList()
        cache.refresh(memberId = 7)
        events.clear()

        assertEquals(Outcome.Success(Unit), store.revoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000)))

        // 서버 로그아웃이 실패(네트워크 없음)해도 기기 정리는 한다.
        assertEquals(listOf("accounts.logout", "tokens.clear(cache=0)"), events)
        assertEquals(WatchSession.None, store.state.value)
        assertNull(tokens.tokens)
    }

    @Test
    fun `다른 회원의 로그아웃 표시는 지금 회원을 지우지 않는다`() = runTest {
        val store = store()
        store.accept(session(8, issuedAt = 1_000))
        events.clear()

        store.revoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000))

        assertEquals(emptyList(), events)
        assertEquals(WatchSession.Active(8, AccountType.LINKED), store.state.value)
    }

    @Test
    fun `측정 중 로그아웃은 RevokePending으로 두었다가 completeRevoke에서 지운다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        events.clear()

        store.deferRevoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000))

        assertEquals(WatchSession.RevokePending(7), store.state.value)
        assertEquals(emptyList(), events)
        assertEquals(WatchSession.RevokePending(7), store().state.value)

        store.completeRevoke()

        assertEquals(listOf("accounts.logout", "tokens.clear(cache=0)"), events)
        assertEquals(WatchSession.None, store.state.value)
    }

    @Test
    fun `RevokePending 중 로그아웃보다 늦게 발급된 같은 회원 세션이 오면 다시 Active다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        store.deferRevoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000))

        store.accept(session(7, issuedAt = 1_500))
        assertEquals(WatchSession.RevokePending(7), store.state.value)

        store.accept(session(7, issuedAt = 3_000))
        assertEquals(WatchSession.Active(7, AccountType.LINKED), store.state.value)
        events.clear()
        store.completeRevoke()
        assertEquals(emptyList(), events)
    }

    @Test
    fun `RevokePending 중 다른 회원 세션은 측정이 끝날 때까지 받지 않는다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        store.deferRevoke(SessionRevokedMessage(7, revokedAtEpochMillis = 2_000))
        events.clear()

        store.accept(session(8, issuedAt = 3_000))

        assertEquals(emptyList(), events)
        assertEquals(WatchSession.RevokePending(7), store.state.value)
    }

    @Test
    fun `토큰 갱신이 거부되어 토큰이 지워지면 None이 된다`() = runTest {
        val store = store()
        store.accept(session(7, issuedAt = 1_000))
        runCurrent()

        tokens.expire()
        runCurrent()

        assertEquals(WatchSession.None, store.state.value)
    }

    @Test
    fun `로그인 정보가 있을 때만 서버 설정 사본을 맞추고 로그아웃하면 지운다(D-379)`() = runTest {
        val store = store()
        assertEquals(Outcome.Failure(AppError.Unauthorized), store.refreshSettings())
        assertEquals(0, settings.refreshes)

        store.accept(WatchSessionMessage(1, "SOCIAL", "a", "r", 100))
        assertEquals(Outcome.Success(Unit), store.refreshSettings())
        assertEquals(1, settings.refreshes)

        store.revoke(SessionRevokedMessage(1, 200))
        assertEquals(1, settings.clears)
    }
}
