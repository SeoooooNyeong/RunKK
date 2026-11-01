package com.a207.runkk.app.data.watch

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import com.a207.runkk.wear.contract.WearPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * 워치에 로그인 정보(WATCH_SESSION)와 로그아웃 표시(SESSION_REVOKED)를 보낸다(3.6, D-371, D-372, SQ-43, SQ-48, FR-240~FR-242).
 *
 * 보내는 때: 로그인 상태이고 워치연동이 켜져 있고 워치가 닿을 때(앱 시작 때 이미 로그인됨, 로그인 완료, 워치연동 켬, 워치 새로 연결)와
 * 워치가 로그인 정보를 요청했을 때(SESSION_REQUEST). 요청은 매니페스트의 [PhoneWatchListenerService]가 받아 [onSessionRequested]로 넘긴다
 * (폰 앱 프로세스가 죽어 있어도 받는다, 코드 리뷰 AN12-1). 앱 안 MessageClient 리스너로 온 같은 요청은 쓰지 않는다(두 번 발급하지 않게).
 * **매번 발급받지 않는다**: 워치에 보낸 회원([Memory])과 지금 회원이 같으면 다시
 * 받지 않고, 처음이거나 회원이 바뀌었거나 워치가 요청했을 때만 [AccountRepository.issueWatchSession]을 부른다(서버 발급 한도 1시간
 * 20회, I14-5). 한도 초과(429)는 실패로 알리지 않고 워치가 가진 세션을 그대로 둔다.
 *
 * 보내는 순서(D-390, H-55): 워치용 토큰 쌍 받기 → WATCH_SESSION(MessageClient만, issuedAt 포함) → 옛 SESSION_REVOKED 항목 지우기.
 * 토큰은 DataClient로 보내지 않는다(항목이 Data Layer 저장소에 남는다, NFR-050).
 *
 * 로그아웃·탈퇴(로그인 상태가 거짓이 됨)와 워치연동 끄기(FR-019) 때는 SESSION_REVOKED(DataClient 항목, revokedAt)를 넣는다. 워치에 보낸
 * 회원이 없으면 넣지 않는다. 로그인 상태에서 워치연동을 끄면 서버의 워치용 갱신 토큰도 폐기한다([AccountRepository.revokeWatchSession],
 * DELETE /auth/watch-session, contract-v2 4절, W25). 폐기가 실패해도 로그아웃 표시는 넣는다. 로그아웃·탈퇴는 서버가 함께 폐기하므로 부르지
 * 않는다. 넣지 못하면 보낸 회원 기억을 남겨 다음 앱 시작·워치 연결 때 다시 넣는다. 게스트도 같다(계정 유형 GUEST). 회원 ID와 계정 유형은 서버 응답이 아니라 폰이 아는 값이다(SQ-43, BE1).
 * 연산들은 하나의 Mutex로 차례로 돈다(발급과 로그아웃 표시가 엇갈리지 않게).
 */
@Singleton
class WatchSessionPublisher internal constructor(
    private val transport: WatchLink.Transport,
    private val sessionRepository: SessionRepository,
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val memory: Memory,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    @Inject
    internal constructor(
        transport: PlayServicesTransport,
        sessionRepository: SessionRepository,
        settings: SettingsRepository,
        accounts: AccountRepository,
        @ApplicationContext context: Context,
        clock: Clock,
        scope: CoroutineScope
    ) : this(transport, sessionRepository, settings, accounts, PreferencesMemory(context), clock, scope)

    /** 워치에 로그인 정보를 보낸 회원 ID(비밀값 아님). 운영은 앱 전용 SharedPreferences, 시험은 메모리다. */
    internal interface Memory {
        fun publishedMemberId(): Long?

        fun remember(memberId: Long)

        fun forget()
    }

    private val lock = Mutex()
    private val started = AtomicBoolean(false)

    /** 로그인 상태·워치연동·워치 연결 변화를 따라 보낸다(워치의 요청은 [onSessionRequested]). 여러 번 불러도 한 번만 시작한다. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // 로그인·워치연동·워치 연결을 한 흐름으로 본다(따로 구독하면 시작 직후 노드 조회와 엇갈려 첫 연결을 놓칠 수 있다).
        // 보낼 수 있고 워치가 닿으면 보내고, 보낼 수 없으면 로그아웃 표시를 넣는다. 워치가 다시 닿을 때도 다시 보므로 넣지 못했던
        // 로그아웃 표시를 다시 넣는다. 두 연산 모두 이미 한 일이면 아무것도 하지 않는다.
        scope.launch {
            var wasAllowed = false
            combine(
                sessionRepository.loggedIn,
                settings.changes().map { it.isWatchLinked },
                transport.connected
            ) { loggedIn, linked, connected -> Gate(isAllowed = loggedIn && linked, isReachable = connected) }
                .distinctUntilChanged()
                .collect { gate ->
                    when {
                        !gate.isAllowed -> revokePublished(wasAllowed)
                        gate.isReachable -> publishIfNeeded()
                    }
                    wasAllowed = gate.isAllowed
                }
        }
    }

    /** 보내도 되는가(로그인·워치연동)와 워치가 닿는가. */
    private data class Gate(val isAllowed: Boolean, val isReachable: Boolean)

    /** 워치가 로그인 정보를 요청했다(FR-242): 이미 보냈어도 새로 받아 그 워치에 보낸다. 로그아웃·워치연동 꺼짐이면 보내지 않는다. */
    suspend fun onSessionRequested(nodeId: String) {
        // 수신 서비스가 프로세스를 막 띄웠으면 설정 사본을 기기에서 읽기 전이라 워치연동이 거짓으로 읽힌다. 사본이 정해질 때까지
        // 기다린 뒤 판정한다(상한을 넘으면 지금 값으로 판정한다).
        withTimeoutOrNull(SETTINGS_SETTLE_MILLIS) { settings.changes().first() }
        lock.withLock {
            val memberId = allowedMemberId() ?: return@withLock
            issueAndSend(memberId, nodeId)
        }
    }

    /** [memberId]의 로그아웃 표시를 넣는다(SQ-48). 넣었으면 보낸 회원 기억을 지운다. */
    suspend fun publishRevoked(memberId: Long) = lock.withLock { revoke(memberId) }

    /** 처음이거나 회원이 바뀌었을 때만 발급받아 닿는 워치에 보낸다. 워치가 닿지 않으면 받지 않는다(발급 한도를 아낀다). */
    private suspend fun publishIfNeeded() = lock.withLock {
        val memberId = allowedMemberId() ?: return@withLock
        if (memory.publishedMemberId() == memberId || !transport.connected.value) return@withLock
        issueAndSend(memberId, nodeId = null)
    }

    /**
     * 로그아웃·워치연동 끄기: 워치에 보낸 회원이 있으면 그 회원의 로그아웃 표시를 넣는다. 로그인 상태에서 워치연동이 꺼졌으면(방금 켜져
     * 있었거나 보낸 회원이 남아 있음) 서버의 워치용 갱신 토큰도 폐기한다. 로그인 직후 설정 사본이 아직 오지 않은 잠깐(연동 거짓)은
     * 켜져 있던 적도 보낸 회원도 없어 폐기하지 않는다.
     */
    private suspend fun revokePublished(wasAllowed: Boolean) = lock.withLock {
        val published = memory.publishedMemberId()
        published?.let { revoke(it) }
        val isLinkTurnedOff = sessionRepository.loggedIn.value && !settings.watchLinked()
        if (isLinkTurnedOff && (wasAllowed || published != null)) revokeServerSessions()
    }

    /** 서버의 워치용 갱신 토큰 폐기(FR-019). 실패는 기록만 한다(워치는 로그아웃 표시로 이미 토큰을 지운다). */
    private suspend fun revokeServerSessions() {
        val revoked = accounts.revokeWatchSession()
        if (revoked is Outcome.Failure) Timber.w("워치용 세션을 서버에서 폐기하지 못했다: %s", revoked.error)
    }

    /** 로그인 상태이고 워치연동이 켜져 있으면 지금 회원 ID. 아니면 null. */
    private fun allowedMemberId(): Long? = accounts.selfMemberId()
        ?.takeIf { sessionRepository.loggedIn.value && settings.watchLinked() }

    private suspend fun issueAndSend(memberId: Long, nodeId: String?) {
        val tokens =
            when (val issued = accounts.issueWatchSession()) {
                is Outcome.Success -> issued.value
                is Outcome.Failure -> return onIssueFailed(issued.error)
            }
        if (!transport.sendNow(WearPaths.WATCH_SESSION, message(memberId, tokens).encode(), nodeId)) {
            Timber.d("워치에 로그인 정보를 보내지 못했다(다음 연결·요청 때 다시 받는다)")
            return
        }
        memory.remember(memberId)
        transport.deleteItems(WearPaths.SESSION_REVOKED)
    }

    private fun message(memberId: Long, tokens: SessionTokens): WatchSessionMessage = WatchSessionMessage(
        memberId = memberId,
        accountType = (if (settings.isGuest()) AccountType.GUEST else AccountType.LINKED).name,
        accessToken = tokens.access,
        refreshToken = tokens.refresh,
        issuedAtEpochMillis = clock.millis()
    )

    /** 발급 한도 초과(429)는 기존 세션 유지라 알리지 않는다. 그 밖의 실패는 기록만 하고 다음 기회에 다시 받는다. */
    private fun onIssueFailed(error: AppError) {
        val isLimited = error == AppError.Server(TOO_MANY_REQUESTS) ||
            (error as? AppError.Rejected)?.code == TOO_MANY_ATTEMPTS
        if (isLimited) {
            Timber.d("워치 로그인 정보 발급 한도: 워치의 기존 세션을 그대로 둔다")
        } else {
            Timber.w("워치 로그인 정보를 받지 못했다: %s", error)
        }
    }

    private suspend fun revoke(memberId: Long) {
        val revoked = SessionRevokedMessage(memberId, clock.millis())
        if (!transport.putItem(WearPaths.SESSION_REVOKED, revoked.encode())) {
            // 보낸 회원 기억을 남겨 다음 앱 시작·워치 연결 때 다시 넣는다(워치에 갱신 토큰이 남지 않게, FR-019).
            Timber.w("워치 로그아웃 표시를 넣지 못했다: 다음 시작·연결 때 다시 넣는다")
            return
        }
        if (memory.publishedMemberId() == memberId) memory.forget()
    }

    /** 앱 전용 SharedPreferences에 보낸 회원 ID만 둔다. 토큰은 두지 않는다. */
    private class PreferencesMemory(context: Context) : Memory {
        private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        override fun publishedMemberId(): Long? = prefs.getLong(KEY_MEMBER, NONE).takeIf { it != NONE }

        override fun remember(memberId: Long) {
            prefs.edit().putLong(KEY_MEMBER, memberId).apply()
        }

        override fun forget() {
            prefs.edit().remove(KEY_MEMBER).apply()
        }

        private companion object {
            const val FILE = "runkk_watch_session"
            const val KEY_MEMBER = "publishedMemberId"
            const val NONE = -1L
        }
    }

    private companion object {
        /** 콜드 스타트에서 설정 사본(기기 파일 한 번 읽기)을 기다리는 상한. */
        const val SETTINGS_SETTLE_MILLIS = 5_000L
        const val TOO_MANY_REQUESTS = 429
        const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
    }
}
