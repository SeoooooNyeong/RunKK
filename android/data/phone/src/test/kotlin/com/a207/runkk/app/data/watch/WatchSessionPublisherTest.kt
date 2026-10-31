package com.a207.runkk.app.data.watch

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.GuestSession
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * WatchSessionPublisher(3.6, D-371, D-372, D-390, SQ-43, SQ-48, FR-019, FR-240~FR-242, NFR-050).
 * 발급 조건(처음·회원 바뀜·워치 요청만), 429는 기존 세션 유지, 로그아웃·워치연동 끄기의 로그아웃 표시, 보내는 순서를 본다.
 */
class WatchSessionPublisherTest {
    private val transport = RecordingTransport()
    private val session = FakeSession(loggedIn = true)
    private val settings = FakeSettings(LINKED)
    private val accounts = FakeAccounts(memberId = 7)
    private val memory = FakeMemory()

    @Test
    fun `로그인·워치연동·연결 상태면 워치용 토큰을 받아 보내고 옛 로그아웃 표시를 지운다(SQ-43, D-390)`() = runTest {
        publisher().start()

        assertEquals(1, accounts.issued)
        assertEquals(
            listOf("sendNow" to WearPaths.WATCH_SESSION, "delete" to WearPaths.SESSION_REVOKED),
            transport.events.map { it.kind to it.path }
        )
        val sent = WatchSessionMessage.decode(transport.of("sendNow", WearPaths.WATCH_SESSION).single().payload!!)
        assertEquals(
            WatchSessionMessage(7, "LINKED", "watch-access", "watch-refresh", NOW.toEpochMilli()),
            sent
        )
        assertEquals(7L, memory.memberId)
    }

    @Test
    fun `비밀값은 DataClient 항목으로 보내지 않는다(NFR-050)`() = runTest {
        publisher().start()
        session.loggedIn.value = false

        transport.events.filter { it.kind == "put" }.forEach { put ->
            assertEquals(WearPaths.SESSION_REVOKED, put.path)
            val body = put.payload!!.toString(Charsets.UTF_8)
            assert("watch-access" !in body && "watch-refresh" !in body) { body }
        }
    }

    @Test
    fun `같은 회원에게 이미 보냈으면 앱을 다시 열어도 발급받지 않는다`() = runTest {
        memory.memberId = 7

        publisher().start()
        transport.connected.value = false
        transport.connected.value = true

        assertEquals(0, accounts.issued)
        assertEquals(emptyList(), transport.events.map { it.path })
    }

    @Test
    fun `워치가 로그인 정보를 요청하면 이미 보냈어도 다시 받아 그 워치에 보낸다(FR-242)`() = runTest {
        memory.memberId = 7
        publisher().start()

        transport.receive(WearPaths.SESSION_REQUEST, ByteArray(0), nodeId = "watch-1")

        assertEquals(1, accounts.issued)
        assertEquals("watch-1", transport.of("sendNow", WearPaths.WATCH_SESSION).single().nodeId)
    }

    @Test
    fun `회원이 바뀌었으면 다시 발급받는다`() = runTest {
        memory.memberId = 3

        publisher().start()

        assertEquals(1, accounts.issued)
        assertEquals(7L, WatchSessionMessage.decode(transport.events.first().payload!!).memberId)
        assertEquals(7L, memory.memberId)
    }

    @Test
    fun `발급 한도 초과(429)는 실패로 알리지 않고 기존 세션을 그대로 둔다`() = runTest {
        memory.memberId = 7
        accounts.result = Outcome.Failure(AppError.Rejected("TOO_MANY_ATTEMPTS"))
        val publisher = publisher()

        publisher.onSessionRequested("w")
        accounts.result = Outcome.Failure(AppError.Server(429))
        publisher.onSessionRequested("w")

        assertEquals(2, accounts.issued)
        assertEquals(emptyList(), transport.events.map { it.path })
        assertEquals(7L, memory.memberId)
    }

    @Test
    fun `그 밖의 발급 실패는 기억을 바꾸지 않고 다음 기회에 다시 받는다`() = runTest {
        accounts.result = Outcome.Failure(AppError.Network)
        publisher().start()
        assertNull(memory.memberId)

        accounts.result = Outcome.Success(TOKENS)
        transport.connected.value = false
        transport.connected.value = true

        assertEquals(2, accounts.issued)
        assertEquals(7L, memory.memberId)
    }

    @Test
    fun `워치에 닿지 않으면 발급받지 않고 연결되면 보낸다`() = runTest {
        transport.connected.value = false
        publisher().start()
        assertEquals(0, accounts.issued)

        transport.connected.value = true

        assertEquals(1, accounts.issued)
        assertEquals(1, transport.of("sendNow", WearPaths.WATCH_SESSION).size)
    }

    @Test
    fun `보내지 못했으면 보냈다고 기억하지 않는다`() = runTest {
        transport.isReachable = false

        publisher().start()

        assertEquals(1, accounts.issued)
        assertNull(memory.memberId)
        assertEquals(emptyList(), transport.of("delete", WearPaths.SESSION_REVOKED))
    }

    @Test
    fun `게스트도 보낸다(계정 유형 GUEST)`() = runTest {
        settings.state.value = LINKED.copy(isGuest = true)

        publisher().start()

        assertEquals("GUEST", WatchSessionMessage.decode(transport.events.first().payload!!).accountType)
    }

    @Test
    fun `로그아웃하면 로그아웃 표시를 DataClient 항목으로 한 번 넣는다(SQ-48, W14)`() = runTest {
        publisher().start()
        transport.events.clear()

        session.loggedIn.value = false
        settings.state.value = SettingsSnapshot.SAFE

        val put = transport.of("put", WearPaths.SESSION_REVOKED).single()
        assertEquals(SessionRevokedMessage(7, NOW.toEpochMilli()), SessionRevokedMessage.decode(put.payload!!))
        assertNull(memory.memberId)
        assertEquals(0, transport.of("sendNow", WearPaths.WATCH_SESSION).size)
    }

    @Test
    fun `워치연동을 끄면 로그아웃 표시를 보내고 워치가 요청해도 로그인 정보를 보내지 않는다(FR-019)`() = runTest {
        publisher().start()
        transport.events.clear()

        settings.state.value = LINKED.copy(isWatchLinked = false)
        transport.receive(WearPaths.SESSION_REQUEST, ByteArray(0), nodeId = "watch-1")

        assertEquals(1, transport.of("put", WearPaths.SESSION_REVOKED).size)
        assertEquals(0, transport.of("sendNow", WearPaths.WATCH_SESSION).size)
        assertEquals(1, accounts.issued)
    }

    @Test
    fun `워치연동을 다시 켜면 새로 발급받아 보낸다`() = runTest {
        publisher().start()
        settings.state.value = LINKED.copy(isWatchLinked = false)

        settings.state.value = LINKED

        assertEquals(2, accounts.issued)
        assertEquals(7L, memory.memberId)
    }

    @Test
    fun `로그아웃 상태로 시작했는데 보낸 회원이 남아 있으면 로그아웃 표시를 보낸다(로그아웃 직후 앱이 꺼진 경우)`() = runTest {
        session.loggedIn.value = false
        settings.state.value = SettingsSnapshot.SAFE
        memory.memberId = 7

        publisher().start()

        assertEquals(1, transport.of("put", WearPaths.SESSION_REVOKED).size)
        assertNull(memory.memberId)
    }

    @Test
    fun `로그아웃 표시를 넣지 못하면 기억을 남겨 워치가 다시 닿을 때 넣는다(FR-019)`() = runTest {
        publisher().start()
        transport.canPutItems = false

        session.loggedIn.value = false
        assertEquals(7L, memory.memberId)

        transport.canPutItems = true
        transport.connected.value = false
        transport.connected.value = true

        assertEquals(1, transport.of("put", WearPaths.SESSION_REVOKED).size)
        assertNull(memory.memberId)
        assertEquals(1, accounts.issued)
    }

    @Test
    fun `보낸 적이 없으면 로그아웃해도 로그아웃 표시를 넣지 않는다`() = runTest {
        session.loggedIn.value = false
        settings.state.value = SettingsSnapshot.SAFE

        publisher().start()

        assertEquals(emptyList(), transport.events.map { it.path })
    }

    @Test
    fun `로그인 직후 설정 사본이 늦게 와도 워치연동이 켜지면 보낸다`() = runTest {
        session.loggedIn.value = false
        settings.state.value = SettingsSnapshot.SAFE
        publisher().start()

        session.loggedIn.value = true
        assertEquals(0, accounts.issued)
        settings.state.value = LINKED

        assertEquals(1, accounts.issued)
    }

    @Test
    fun `publishRevoked는 그 회원의 로그아웃 표시를 넣는다`() = runTest {
        publisher().publishRevoked(9)

        assertEquals(
            SessionRevokedMessage(9, NOW.toEpochMilli()),
            SessionRevokedMessage.decode(transport.of("put", WearPaths.SESSION_REVOKED).single().payload!!)
        )
    }

    private fun TestScope.publisher(): WatchSessionPublisher {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return WatchSessionPublisher(transport, session, settings, accounts, memory, CLOCK, scope)
    }

    private class FakeMemory : WatchSessionPublisher.Memory {
        var memberId: Long? = null

        override fun publishedMemberId(): Long? = memberId

        override fun remember(memberId: Long) {
            this.memberId = memberId
        }

        override fun forget() {
            memberId = null
        }
    }

    private class FakeSession(loggedIn: Boolean) : SessionRepository {
        override val loggedIn = MutableStateFlow(loggedIn)

        override suspend fun save(tokens: SessionTokens): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun saveGuestCredential(credential: String): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun clearGuestCredential(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun hasGuestCredential(): Boolean = false
    }

    private class FakeSettings(initial: SettingsSnapshot) : SettingsRepository {
        val state = MutableStateFlow(initial)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

        override fun locationConsented() = state.value.isLocationConsented

        override fun heartRateConsented() = state.value.isHeartRateConsented

        override fun watchLinked() = state.value.isWatchLinked

        override fun courseMapShared() = state.value.isCourseMapShared

        override fun birthYear() = state.value.birthYear

        override fun guidanceSettings() = state.value.guidanceSettings

        override fun isGuest(): Boolean = state.value.isGuest

        override fun changes(): Flow<SettingsSnapshot> = state

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    /** 워치용 발급만 쓰는 계정 포트. 나머지 연산은 이 시험에서 부르지 않는다. */
    private class FakeAccounts(private val memberId: Long?) : AccountRepository {
        var issued = 0
        var result: Outcome<SessionTokens> = Outcome.Success(TOKENS)

        override fun selfMemberId(): Long? = memberId

        override suspend fun issueWatchSession(): Outcome<SessionTokens> {
            issued++
            return result
        }

        override suspend fun login(token: ProviderToken): Outcome<SessionTokens> = unused()

        override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> = unused()

        override suspend fun logout(): Outcome<Unit> = unused()

        override suspend fun startAsGuest(consents: Map<String, Boolean>): Outcome<GuestSession> = unused()

        override suspend fun linkSocialAccount(token: ProviderToken): Outcome<Unit> = unused()

        override suspend fun withdraw(): Outcome<Unit> = unused()

        override suspend fun profile(memberId: Long): Outcome<Profile> = unused()

        override suspend fun mySummary(): Outcome<MySummary> = unused()

        override suspend fun editProfile(edit: ProfileEdit): Outcome<Unit> = unused()

        override suspend fun changeSetting(item: String, value: String): Outcome<Unit> = unused()

        override suspend fun giveConsent(item: String): Outcome<Unit> = unused()

        override suspend fun withdrawConsent(item: String): Outcome<Unit> = unused()

        override suspend fun legalDocument(type: String): Outcome<String> = unused()

        override suspend fun settings(): Outcome<MemberSettings> = unused()

        private fun unused(): Nothing = error("이 시험에서 부르지 않는다")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-01T01:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TOKENS = SessionTokens("watch-access", "watch-refresh")
        val LINKED =
            SettingsSnapshot(
                isLocationConsented = true,
                isHeartRateConsented = false,
                isWatchLinked = true,
                isCourseMapShared = false,
                birthYear = 1990,
                guidanceSettings = GuidanceSettings.DEFAULT
            )
    }
}
