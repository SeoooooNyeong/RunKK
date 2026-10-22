package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope(Dispatchers.Main)를 시험 디스패처로 바꾼다. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

val sampleProfile = Profile(
    memberId = 7,
    displayName = "달리는곰",
    disclosure = "SELF",
    tier = "골드",
    level = 3,
    tags = setOf("SOCIAL_RUN", "EVENING"),
    intro = "퇴근 뒤 한강",
    runimalType = "MORNING_SPARROW",
    faceKey = null,
    crewName = "새벽크루",
    longestDistance = null,
    ageBand = "30대",
    gender = Gender.FEMALE
)

/** 호출을 기록하고 연산마다 실패를 끼울 수 있는 가짜 AccountRepository. */
class FakeAccountRepository : AccountRepository {
    val calls = mutableListOf<String>()
    val failures = mutableMapOf<String, AppError>()
    var loginResult: Outcome<SessionTokens> = Outcome.Success(SessionTokens("a", "r"))
    var profile: Profile = sampleProfile
    var settings = MemberSettings(
        values = mapOf("UNIT" to "KM", "RUN_SETTINGS.voice" to "true", "RECORD_VISIBILITY" to "PUBLIC"),
        consents = mapOf("LOCATION" to true, "HEART_RATE" to false, "MARKETING" to false)
    )
    var legal: String = "약관 본문"
    var lastForm: SignUpForm? = null
    var lastEdit: Triple<String, String?, Set<String>>? = null

    private fun <T> call(name: String, value: () -> T): Outcome<T> {
        calls += name
        return failures[name]?.let { Outcome.Failure(it) } ?: Outcome.Success(value())
    }

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> {
        calls += "login:${token.provider}"
        return loginResult
    }

    override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> {
        lastForm = form
        return call("signUp") { SessionTokens("a", "r") }
    }

    override suspend fun logout(): Outcome<Unit> = call("logout") {}

    override suspend fun withdraw(): Outcome<Unit> = call("withdraw") {}

    override suspend fun profile(memberId: Long): Outcome<Profile> = call("profile:$memberId") { profile }

    override suspend fun editProfile(nickname: String, intro: String?, tags: Set<String>): Outcome<Unit> {
        lastEdit = Triple(nickname, intro, tags)
        return call("editProfile") {}
    }

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> =
        call("changeSetting:$item=$value") {
        }

    override suspend fun giveConsent(item: String): Outcome<Unit> = call("giveConsent:$item") {}

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = call("withdrawConsent:$item") {}

    override suspend fun legalDocument(type: String): Outcome<String> = call("legal:$type") { legal }

    override suspend fun settings(): Outcome<MemberSettings> = call("settings") { settings }

    var faceResult: Outcome<List<com.a207.runkk.app.core.model.FaceLayer>> = Outcome.Success(emptyList())

    override suspend fun faceLayers(faceKey: String?): Outcome<List<com.a207.runkk.app.core.model.FaceLayer>> {
        calls += "face:$faceKey"
        return faceResult
    }
}

class FakeSessionRepository : SessionRepository {
    private val state = MutableStateFlow(false)
    override val loggedIn: StateFlow<Boolean> = state

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> {
        state.value = true
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> {
        state.value = false
        return Outcome.Success(Unit)
    }
}

class FakeSettingsRepository : SettingsRepository {
    var refreshes = 0

    override suspend fun refresh(): Outcome<Unit> {
        refreshes++
        return Outcome.Success(Unit)
    }

    override fun locationConsented() = false

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

class FakePendingRunRepository : PendingRunRepository {
    var cleared = false

    override suspend fun save(run: CompletedRun) = Outcome.Success(Unit)

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(emptyList())

    override fun observePending(): Flow<List<CompletedRun>> = emptyFlow()

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates) = Outcome.Success(Unit)

    override suspend fun clear(): Outcome<Unit> {
        cleared = true
        return Outcome.Success(Unit)
    }
}

/** 제공자 SDK 대신 정한 결과를 돌려준다. */
class FakeSocialLoginClient(
    override val provider: ProviderType,
    var result: Outcome<ProviderToken> = Outcome.Success(ProviderToken(provider, "token"))
) : SocialLoginClient {
    var calls = 0

    override suspend fun signIn(context: Context): Outcome<ProviderToken> {
        calls++
        return result
    }
}
