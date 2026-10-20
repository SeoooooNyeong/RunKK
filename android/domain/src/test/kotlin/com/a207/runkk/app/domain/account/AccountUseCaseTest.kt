package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

private class FakeAccountRepository(private val log: CallLog) : AccountRepository {
    val failures = mutableMapOf<String, AppError>()
    private val tokens = SessionTokens("access", "refresh")

    private fun <T> call(name: String, value: T): Outcome<T> {
        log.add("account.$name")
        return failures[name]?.let { Outcome.Failure(it) } ?: Outcome.Success(value)
    }

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> = call("login", tokens)

    override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> = call("signUp", tokens)

    override suspend fun logout(): Outcome<Unit> = call("logout", Unit)

    override suspend fun withdraw(): Outcome<Unit> = call("withdraw", Unit)

    override suspend fun profile(memberId: Long): Outcome<Profile> = error("unused")

    override suspend fun editProfile(nickname: String, intro: String?, tags: Set<String>): Outcome<Unit> =
        call("editProfile", Unit)

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> =
        call("changeSetting:$item=$value", Unit)

    override suspend fun giveConsent(item: String): Outcome<Unit> = call("giveConsent:$item", Unit)

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = call("withdrawConsent:$item", Unit)

    override suspend fun legalDocument(type: String): Outcome<String> = call("legalDocument", "")
}

private class FakeSessionRepository(private val log: CallLog) : SessionRepository {
    private val state = MutableStateFlow(false)
    override val loggedIn: StateFlow<Boolean> = state
    var saved: SessionTokens? = null

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> {
        log.add("session.save")
        saved = tokens
        state.value = true
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> {
        log.add("session.clear")
        state.value = false
        return Outcome.Success(Unit)
    }
}

/** LoginUseCase·LogoutUseCase·WithdrawUseCase·ChangeSettingUseCase: 호출 순서와 실패 때 멈춤(14장). */
class AccountUseCaseTest {
    private val log = CallLog()
    private val account = FakeAccountRepository(log)
    private val session = FakeSessionRepository(log)
    private val settings = FakeSettingsRepository(log = log)
    private val pending = FakePendingRunRepository(log)
    private val kakao = ProviderToken(ProviderType.KAKAO, "kakao-token")

    @Test
    fun `로그인 - login → session save → settings refresh`() = runTest {
        assertEquals(Outcome.Success(Unit), LoginUseCase(account, session, settings)(kakao))
        assertEquals(listOf("account.login", "session.save", "settings.refresh"), log.calls)
        assertEquals(true, session.loggedIn.value)
    }

    @Test
    fun `로그인 - 서버 로그인이 실패하면 멈춘다`() = runTest {
        account.failures["login"] = AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED)
        val result = LoginUseCase(account, session, settings)(kakao)
        assertEquals(Outcome.Failure(AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED)), result)
        assertEquals(listOf("account.login"), log.calls)
    }

    @Test
    fun `로그인 - 설정 사본 갱신 실패는 로그인을 막지 않는다(앱 시작 때 다시 받는다)`() = runTest {
        settings.refreshResult = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Success(Unit), LoginUseCase(account, session, settings)(kakao))
    }

    @Test
    fun `가입 - signUp → session save → settings refresh`() = runTest {
        val form =
            SignUpForm(
                nickname = "러너",
                birthYear = 1996,
                gender = Gender.UNDISCLOSED,
                intro = null,
                consents = mapOf("TERMS" to true),
                recordVisibility = "PUBLIC",
                tags = emptySet()
            )
        assertEquals(Outcome.Success(Unit), LoginUseCase(account, session, settings).signUp(form))
        assertEquals(listOf("account.signUp", "session.save", "settings.refresh"), log.calls)
    }

    @Test
    fun `로그아웃 - 미전송 기록은 지우지 않는다`() = runTest {
        assertEquals(Outcome.Success(Unit), LogoutUseCase(account, session, settings)())
        assertEquals(listOf("account.logout", "session.clear", "settings.clear"), log.calls)
    }

    @Test
    fun `로그아웃 - 서버 로그아웃이 실패해도 기기의 세션·설정 사본은 지운다`() = runTest {
        account.failures["logout"] = AppError.Network
        LogoutUseCase(account, session, settings)()
        assertEquals(listOf("account.logout", "session.clear", "settings.clear"), log.calls)
    }

    @Test
    fun `탈퇴 - withdraw → pending clear → settings clear → session clear`() = runTest {
        assertEquals(Outcome.Success(Unit), WithdrawUseCase(account, pending, settings, session)())
        assertEquals(
            listOf("account.withdraw", "pending.clear", "settings.clear", "session.clear"),
            log.calls
        )
    }

    @Test
    fun `탈퇴 - 서버 탈퇴가 실패하면 기기 자료를 지우지 않는다`() = runTest {
        account.failures["withdraw"] = AppError.Network
        assertEquals(Outcome.Failure(AppError.Network), WithdrawUseCase(account, pending, settings, session)())
        assertEquals(listOf("account.withdraw"), log.calls)
    }

    @Test
    fun `설정 변경 - 요청 종류마다 다른 연산 뒤 사본 갱신`() = runTest {
        val useCase = ChangeSettingUseCase(account, settings)
        useCase(SettingChangeRequest.Change("UNIT", "MI"))
        useCase(SettingChangeRequest.GiveConsent("HEART_RATE"))
        useCase(SettingChangeRequest.WithdrawConsent("LOCATION"))
        assertEquals(
            listOf(
                "account.changeSetting:UNIT=MI",
                "settings.refresh",
                "account.giveConsent:HEART_RATE",
                "settings.refresh",
                "account.withdrawConsent:LOCATION",
                "settings.refresh"
            ),
            log.calls
        )
    }

    @Test
    fun `설정 변경 - 서버 변경이 실패하면 사본을 갱신하지 않고, 갱신 실패는 그대로 알린다`() = runTest {
        account.failures["withdrawConsent:LOCATION"] = AppError.Network
        val useCase = ChangeSettingUseCase(account, settings)
        assertEquals(Outcome.Failure(AppError.Network), useCase(SettingChangeRequest.WithdrawConsent("LOCATION")))
        assertEquals(listOf("account.withdrawConsent:LOCATION"), log.calls)

        settings.refreshResult = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Failure(AppError.Network), useCase(SettingChangeRequest.Change("UNIT", "KM")))
    }
}
