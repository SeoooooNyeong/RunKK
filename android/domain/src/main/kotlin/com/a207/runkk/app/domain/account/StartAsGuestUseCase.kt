package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 게스트로 시작(FR-197, FR-004, FR-028): AccountRepository.startAsGuest → 게스트 자격 증명 저장 → 토큰 저장 →
 * SettingsRepository.refresh. 자격 증명을 먼저 저장하고, 둘 중 하나라도 실패하면 둘 다 지우고 실패로 끝낸다(토큰만 남은 반쪽 상태 방지,
 * D-159 (1)). 설정 사본 갱신 실패는 시작을 막지 않는다(LoginUseCase와 같은 뒤 단계).
 */
class StartAsGuestUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(consents: Map<String, Boolean>): Outcome<Unit> {
        val started =
            when (val result = account.startAsGuest(consents)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val credentialSaved = session.saveGuestCredential(started.guestCredential)
        val saved = if (credentialSaved is Outcome.Failure) credentialSaved else session.save(started.tokens)
        if (saved is Outcome.Failure) {
            session.clearGuestCredential()
            session.clear()
            return saved
        }
        settings.refresh()
        return Outcome.Success(Unit)
    }
}
