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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 워치에 로그인 정보(WATCH_SESSION)와 로그아웃 표시(SESSION_REVOKED)를 보낸다(3.6, D-371, D-372, SQ-43, SQ-48, FR-240~FR-242).
 *
 * 보내는 때: 로그인 상태이고 워치연동이 켜져 있고 워치가 닿을 때(앱 시작 때 이미 로그인됨, 로그인 완료, 워치연동 켬, 워치 새로 연결)와
 * 워치가 로그인 정보를 요청했을 때(SESSION_REQUEST). **매번 발급받지 않는다**: 워치에 보낸 회원([Memory])과 지금 회원이 같으면 다시
 * 받지 않고, 처음이거나 회원이 바뀌었거나 워치가 요청했을 때만 [AccountRepository.issueWatchSession]을 부른다(서버 발급 한도 1시간
 * 20회, I14-5). 한도 초과(429)는 실패로 알리지 않고 워치가 가진 세션을 그대로 둔다.
 *
 * 보내는 순서(D-390, H-55): 워치용 토큰 쌍 받기 → WATCH_SESSION(MessageClient만, issuedAt 포함) → 옛 SESSION_REVOKED 항목 지우기.
 * 토큰은 DataClient로 보내지 않는다(항목이 Data Layer 저장소에 남는다, NFR-050).
 *
 * 로그아웃·탈퇴(로그인 상태가 거짓이 됨)와 워치연동 끄기(FR-019) 때는 SESSION_REVOKED(DataClient 항목, revokedAt)를 넣는다. 워치에 보낸
 * 회원이 없으면 넣지 않는다. 게스트도 같다(계정 유형 GUEST). 회원 ID와 계정 유형은 서버 응답이 아니라 폰이 아는 값이다(SQ-43, BE1).
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

    /** 로그인 상태·워치연동·워치 연결 변화와 워치의 요청을 따라 보낸다. 여러 번 불러도 한 번만 시작한다. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            transport.incoming
                .filter { it.path == WearPaths.SESSION_REQUEST }
                .mapNotNull { it.sourceNodeId }
                .collect { onSessionRequested(it) }
        }
        scope.launch {
            combine(sessionRepository.loggedIn, settings.changes().map { it.isWatchLinked }) { loggedIn, linked ->
                loggedIn && linked
            }.distinctUntilChanged()
                .collect { isAllowed -> if (isAllowed) publishIfNeeded() else revokePublished() }
        }
        scope.launch {
            transport.connected
                .drop(1)
                .filter { it }
                .collect { publishIfNeeded() }
        }
    }

    /** 워치가 로그인 정보를 요청했다(FR-242): 이미 보냈어도 새로 받아 그 워치에 보낸다. 로그아웃·워치연동 꺼짐이면 보내지 않는다. */
    suspend fun onSessionRequested(nodeId: String) = lock.withLock {
        val memberId = allowedMemberId() ?: return@withLock
        issueAndSend(memberId, nodeId)
    }

    /** [memberId]의 로그아웃 표시를 넣는다(SQ-48). 넣었으면 보낸 회원 기억을 지운다. */
    suspend fun publishRevoked(memberId: Long) = lock.withLock { revoke(memberId) }

    /** 처음이거나 회원이 바뀌었을 때만 발급받아 닿는 워치에 보낸다. 워치가 닿지 않으면 받지 않는다(발급 한도를 아낀다). */
    private suspend fun publishIfNeeded() = lock.withLock {
        val memberId = allowedMemberId() ?: return@withLock
        if (memory.publishedMemberId() == memberId || !transport.connected.value) return@withLock
        issueAndSend(memberId, nodeId = null)
    }

    /** 로그아웃·워치연동 끄기: 워치에 보낸 회원이 있으면 그 회원의 로그아웃 표시를 넣는다. */
    private suspend fun revokePublished() = lock.withLock {
        memory.publishedMemberId()?.let { revoke(it) }
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
        if (transport.putItem(WearPaths.SESSION_REVOKED, revoked.encode())) {
            if (memory.publishedMemberId() == memberId) memory.forget()
        }
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
        const val TOO_MANY_REQUESTS = 429
        const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
    }
}
