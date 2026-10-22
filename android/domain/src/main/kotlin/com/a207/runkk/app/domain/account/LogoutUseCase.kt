package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 로그아웃(FR-002): AccountRepository.logout → SessionRepository.clear → SettingsRepository.clear.
 * 미전송 기록은 지우지 않는다. 서버 로그아웃이 실패해도(네트워크 없음) 기기의 세션·설정 사본은 지운다.
 */
class LogoutUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(): Outcome<Unit> {
        account.logout()
        val cleared = session.clear()
        val settingsCleared = settings.clear()
        return listOf(cleared, settingsCleared).firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}
