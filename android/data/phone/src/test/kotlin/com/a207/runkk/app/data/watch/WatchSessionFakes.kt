package com.a207.runkk.app.data.watch

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
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

/** WatchSessionPublisher 시험 가짜(발행기 시험과 수신 서비스 시험이 함께 쓴다). */
internal val WATCH_TOKENS = SessionTokens("watch-access", "watch-refresh")

internal class FakeWatchMemory : WatchSessionPublisher.Memory {
    var memberId: Long? = null

    override fun publishedMemberId(): Long? = memberId

    override fun remember(memberId: Long) {
        this.memberId = memberId
    }

    override fun forget() {
        memberId = null
    }
}

internal class FakeWatchSession(loggedIn: Boolean) : SessionRepository {
    override val loggedIn = MutableStateFlow(loggedIn)

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun saveGuestCredential(credential: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun clearGuestCredential(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun hasGuestCredential(): Boolean = false
}

/**
 * 설정 사본(가짜). [isSettled]가 거짓이면 운영 DataStoreSettingsRepository처럼 기기 사본을 아직 읽지 못한 상태다: 읽기 연산은 안전한 값
 * (워치연동 꺼짐)을 돌려주고 changes()는 아무것도 내지 않는다.
 */
internal class FakeWatchSettings(initial: SettingsSnapshot) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val isSettled = MutableStateFlow(true)

    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = state.value.isLocationConsented

    override fun heartRateConsented() = state.value.isHeartRateConsented

    override fun watchLinked() = isSettled.value && state.value.isWatchLinked

    override fun courseMapShared() = state.value.isCourseMapShared

    override fun birthYear() = state.value.birthYear

    override fun guidanceSettings() = state.value.guidanceSettings

    override fun isGuest(): Boolean = state.value.isGuest

    override fun changes(): Flow<SettingsSnapshot> =
        combine(state, isSettled) { snapshot, settled -> snapshot.takeIf { settled } }.filterNotNull()

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 워치용 발급·폐기만 쓰는 계정 포트. 나머지 연산은 이 시험에서 부르지 않는다. */
internal class FakeWatchAccounts(private val memberId: Long?) : AccountRepository {
    var issued = 0
    var result: Outcome<SessionTokens> = Outcome.Success(WATCH_TOKENS)
    var revokes = 0
    var revokeResult: Outcome<Unit> = Outcome.Success(Unit)

    override fun selfMemberId(): Long? = memberId

    override suspend fun issueWatchSession(): Outcome<SessionTokens> {
        issued++
        return result
    }

    override suspend fun revokeWatchSession(): Outcome<Unit> {
        revokes++
        return revokeResult
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
