package com.a207.runkk.watch.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.run.FreeRunGoal
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * 워치의 로그인 정보와 회원ID를 보관한다(3.3, FR-240~FR-242, D-372, 상태도 2.4.26).
 *
 * - 토큰 쌍은 공용 :data의 EncryptedTokenStore(SessionRepository)에 둔다(NFR-050). 이 클래스는 비밀값이 아닌 머리 정보(회원ID·계정 유형·
 *   발급 시각·상태·로그아웃 시각)만 앱 전용 저장소([prefs])에 둔다. 프로세스가 막 떠 받은 로그아웃 표시도 발급 시각과 견줄 수 있게
 *   첫 상태를 생성 때 바로 읽는다.
 * - 도착 순서(H-55, D-390): 받아 둔 세션의 issuedAt보다 이른 revokedAt, 또는 다른 회원ID의 로그아웃 표시는 버린다. 반대로 마지막
 *   로그아웃보다 이르거나 같은 때 발급된 같은 회원의 세션도 버린다(끊긴 동안 늦게 온 옛 세션).
 * - 다른 회원의 로그인 정보가 오면 보관함 → 상대 기록 → 토큰 순으로 지운다(purgeOtherMember, H-56). 보관함을 지우지 못하면 새 회원을
 *   받지 않는다(이전 회원 기록이 새 회원 토큰으로 올라가지 않게). 로그아웃 대기(RevokePending, 측정 중) 동안 온 다른 회원은 받지 않는다:
 *   진행 중 기록이 새 회원 보관함에 들어가지 않게 하고, 결과를 닫은 뒤 다시 요청하면 폰이 다시 보낸다.
 * - 로그아웃(revoke·completeRevoke)은 서버 로그아웃(최선 노력) → 상대 기록 → 토큰 순으로 지운다. 미전송 기록은 남긴다(FR-241).
 * - 공용 :data TokenAuthenticator가 갱신을 거부당해 토큰을 지우면 None이 된다(상태도 "갱신 실패").
 * - 서버 설정 사본(위치 수집 동의·안내 설정 등, D-379)은 회원 사본이라 로그인 정보와 수명을 같이 한다: 화면이 메인을 열 때
 *   [refreshSettings]로 맞추고(폰 LoginUseCase가 로그인 뒤 맞추는 것과 같다), 로그아웃·다른 회원 로그인 때 지운다. 화면은 이 사본을
 *   직접 보지 않고 [freeRunGoal]로 자유런 목표만 받는다(watch.ui Fan-Out 7 유지).
 * 모든 바꾸기는 [mutex] 하나로 한 줄로 세운다.
 */
@Singleton
class WatchSessionStore internal constructor(
    private val tokens: SessionRepository,
    private val accounts: AccountRepository,
    private val pendingRuns: PendingRunRepository,
    private val cache: OpponentRecordCache,
    private val uploads: UploadScheduler,
    private val settings: SettingsRepository,
    private val prefs: SharedPreferences,
    scope: CoroutineScope
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        tokens: SessionRepository,
        accounts: AccountRepository,
        pendingRuns: PendingRunRepository,
        cache: OpponentRecordCache,
        uploads: UploadScheduler,
        settings: SettingsRepository,
        scope: CoroutineScope
    ) : this(
        tokens,
        accounts,
        pendingRuns,
        cache,
        uploads,
        settings,
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
        scope
    )

    private val mutex = Mutex()

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<WatchSession> = _state.asStateFlow()

    init {
        scope.launch { tokens.loggedIn.collect { isLoggedIn -> if (!isLoggedIn) onTokensCleared() } }
    }

    suspend fun accept(m: WatchSessionMessage): Outcome<Unit> = mutex.withLock {
        val previousMember = storedMemberId()
        val isSameMember = previousMember == m.memberId
        val issuedAt = storedLong(KEY_ISSUED_AT)
        val revokedAt = storedLong(KEY_REVOKED_AT)
        val isStale = isSameMember &&
            (
                (issuedAt != null && m.issuedAtEpochMillis < issuedAt) ||
                    (revokedAt != null && m.issuedAtEpochMillis <= revokedAt)
                )
        if (isStale) {
            Timber.i("옛 로그인 정보를 버린다")
            return@withLock Outcome.Success(Unit)
        }
        if (_state.value is WatchSession.RevokePending && !isSameMember) {
            Timber.i("측정이 끝날 때까지 다른 회원 로그인 정보를 받지 않는다")
            return@withLock Outcome.Success(Unit)
        }
        if (previousMember != null && !isSameMember) {
            val purged = purgeOtherMember()
            if (purged is Outcome.Failure) return@withLock purged
        }
        val saved = tokens.save(SessionTokens(m.accessToken, m.refreshToken))
        if (saved is Outcome.Failure) return@withLock saved
        val accountType = accountTypeOf(m.accountType)
        prefs.edit(commit = true) {
            putLong(KEY_MEMBER, m.memberId)
            putString(KEY_ACCOUNT_TYPE, accountType.name)
            putLong(KEY_ISSUED_AT, m.issuedAtEpochMillis)
            putString(KEY_STATUS, STATUS_ACTIVE)
            remove(KEY_REVOKED_AT)
        }
        _state.value = WatchSession.Active(m.memberId, accountType)
        // 같은 회원이 다시 오면 남은 미전송 기록을 바로 보낸다(FR-241, NFR-051).
        uploads.requestUpload()
        Outcome.Success(Unit)
    }

    /** 지금 지운다(측정 중이 아님). 낡은 표시면 버린다. */
    suspend fun revoke(m: SessionRevokedMessage): Outcome<Unit> = mutex.withLock {
        if (isStale(m)) return@withLock Outcome.Success(Unit)
        finishRevoke(m.revokedAtEpochMillis)
    }

    /** 측정 중이라 지움을 미룬다. 결과를 닫을 때 [completeRevoke]가 지운다. 낡은 표시면 버린다. */
    suspend fun deferRevoke(m: SessionRevokedMessage): Outcome<Unit> = mutex.withLock {
        if (isStale(m)) return@withLock Outcome.Success(Unit)
        val memberId = storedMemberId()
        if (memberId == null || _state.value == WatchSession.None) return@withLock Outcome.Success(Unit)
        // 대기 중 표시가 거듭 오면 가장 늦은 로그아웃 시각을 남긴다(그보다 이른 세션을 버리는 기준).
        val revokedAt = maxOf(m.revokedAtEpochMillis, storedLong(KEY_REVOKED_AT) ?: Long.MIN_VALUE)
        prefs.edit(commit = true) {
            putString(KEY_STATUS, STATUS_REVOKE_PENDING)
            putLong(KEY_REVOKED_AT, revokedAt)
        }
        _state.value = WatchSession.RevokePending(memberId)
        Outcome.Success(Unit)
    }

    /** 미뤄 둔 로그아웃을 마친다(결과 화면을 닫음). 로그아웃 대기가 아니면 아무것도 하지 않는다. */
    suspend fun completeRevoke(): Outcome<Unit> = mutex.withLock {
        if (_state.value !is WatchSession.RevokePending) return@withLock Outcome.Success(Unit)
        finishRevoke(storedLong(KEY_REVOKED_AT) ?: 0L)
    }

    /**
     * 서버 설정 사본을 맞춘다(D-379, 메인을 열 때). 로그인 정보가 없으면 부르지 않고 Unauthorized다. 실패하면 이전 사본을 그대로 둔다.
     * 바꾸기와 겹치지 않게 같은 잠금 안에서 부른다(로그아웃 직후 늦게 온 사본이 남지 않게).
     */
    suspend fun refreshSettings(): Outcome<Unit> = mutex.withLock {
        if (_state.value !is WatchSession.Active) return@withLock Outcome.Failure(AppError.Unauthorized)
        settings.refresh()
    }

    /**
     * 설정 사본으로 고른 자유런 목표(FR-048, FR-244). 규칙은 폰과 같은 domain FreeRunGoal.of다: 코치 계획 따르기가 켜져 있고
     * [todayPlan]이 있으면 그 계획, 아니면 자유런 설정의 목표 거리다. 사본이 없으면 목표 없음(GuidanceSettings.DEFAULT)이다.
     */
    fun freeRunGoal(todayPlan: PlanSnapshot?): FreeRunGoal = FreeRunGoal.of(todayPlan, settings.guidanceSettings())

    /** 받아 둔 세션보다 이른 표시(H-55)이거나 다른 회원의 표시. */
    private fun isStale(m: SessionRevokedMessage): Boolean {
        val issuedAt = storedLong(KEY_ISSUED_AT)
        val memberId = storedMemberId()
        val isStale = (issuedAt != null && m.revokedAtEpochMillis < issuedAt) ||
            (memberId != null && memberId != m.memberId)
        if (isStale) Timber.i("낡은 로그아웃 표시를 버린다")
        return isStale
    }

    /** 서버 로그아웃(최선 노력, 워치 갱신 토큰) → 상대 기록 → 토큰. mutex 안에서 부른다. */
    private suspend fun finishRevoke(revokedAt: Long): Outcome<Unit> {
        if (withTimeoutOrNull(LOGOUT_TIMEOUT_MILLIS) { accounts.logout() } !is Outcome.Success) {
            Timber.i("서버 로그아웃을 마치지 못했다. 기기 정리는 한다")
        }
        cache.clear()
        settings.clear()
        val cleared = tokens.clear()
        accounts.forgetMyGender()
        prefs.edit(commit = true) {
            putString(KEY_STATUS, STATUS_NONE)
            remove(KEY_ISSUED_AT)
            putLong(KEY_REVOKED_AT, revokedAt)
        }
        _state.value = WatchSession.None
        return cleared
    }

    /** 이전 회원의 보관함 → 상대 기록 → 토큰(H-56). 보관함을 지우지 못하면 멈추고 실패를 돌려준다. mutex 안에서 부른다. */
    private suspend fun purgeOtherMember(): Outcome<Unit> {
        val purged = pendingRuns.clear()
        if (purged is Outcome.Failure) {
            Timber.w("이전 회원의 미전송 기록을 지우지 못해 새 로그인 정보를 받지 않는다")
            return purged
        }
        cache.clear()
        settings.clear()
        accounts.forgetMyGender()
        return tokens.clear()
    }

    /** 토큰이 지워졌다(갱신 거부). 저장 중 잠깐 지운 경우(purge 뒤 save)는 잠금이 풀린 뒤 다시 보아 거른다. */
    private suspend fun onTokensCleared() = mutex.withLock {
        if (tokens.loggedIn.value || _state.value !is WatchSession.Active) return@withLock
        prefs.edit(commit = true) {
            putString(KEY_STATUS, STATUS_NONE)
            remove(KEY_ISSUED_AT)
        }
        _state.value = WatchSession.None
    }

    private fun restore(): WatchSession {
        val memberId = storedMemberId() ?: return WatchSession.None
        return when (prefs.getString(KEY_STATUS, STATUS_NONE)) {
            STATUS_ACTIVE -> WatchSession.Active(memberId, accountTypeOf(prefs.getString(KEY_ACCOUNT_TYPE, null)))
            STATUS_REVOKE_PENDING -> WatchSession.RevokePending(memberId)
            else -> WatchSession.None
        }
    }

    private fun storedMemberId(): Long? = storedLong(KEY_MEMBER)

    private fun storedLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private companion object {
        const val FILE = "watch_session"
        const val KEY_MEMBER = "member_id"
        const val KEY_ACCOUNT_TYPE = "account_type"
        const val KEY_ISSUED_AT = "issued_at"
        const val KEY_REVOKED_AT = "revoked_at"
        const val KEY_STATUS = "status"
        const val STATUS_NONE = "NONE"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_REVOKE_PENDING = "REVOKE_PENDING"

        /** 서버 로그아웃을 기다리는 상한. 넘으면 기기 정리만 한다. */
        const val LOGOUT_TIMEOUT_MILLIS = 15_000L

        /** 서버 AccountType 상수(GUEST·SOCIAL 등) → 앱 계정 종류. 게스트가 아니면 연동 회원이다. */
        fun accountTypeOf(name: String?): AccountType =
            if (name == AccountType.GUEST.name) AccountType.GUEST else AccountType.LINKED
    }
}
