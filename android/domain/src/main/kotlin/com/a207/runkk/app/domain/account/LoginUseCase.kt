package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 로그인(FR-001, FR-003): AccountRepository.login → SessionRepository.save → SettingsRepository.refresh.
 * 앞 단계가 실패하면 멈춘다. 설정 사본 갱신 실패는 로그인을 막지 않는다(앱 시작 때 RunkkApp이 다시 받는다).
 * 가입은 SignUpUseCase가 같은 순서로 세션을 만든다.
 */
class LoginUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(token: ProviderToken): Outcome<Unit> =
        startSession(session, settings, account.login(token))
}

/** 가입(FR-003, FR-004, FR-028): AccountRepository.signUp → SessionRepository.save → SettingsRepository.refresh. */
class SignUpUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(form: SignUpForm): Outcome<Unit> = startSession(session, settings, account.signUp(form))
}

/** 로그인·가입이 같이 쓰는 뒤 단계: 토큰 저장(실패하면 멈춤) → 설정 사본 갱신(실패해도 이어 감). */
private suspend fun startSession(
    session: SessionRepository,
    settings: SettingsRepository,
    issued: Outcome<SessionTokens>
): Outcome<Unit> {
    val tokens =
        when (issued) {
            is Outcome.Failure -> return issued
            is Outcome.Success -> issued.value
        }
    val saved = session.save(tokens)
    if (saved is Outcome.Failure) return saved
    settings.refresh()
    return Outcome.Success(Unit)
}
