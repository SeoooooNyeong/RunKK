package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 게스트 계정에 소셜 계정을 연동(FR-198): AccountRepository.linkSocialAccount → 게스트 자격 증명 삭제(서버도 해시를 지운다,
 * D-89 (4), D-159 (2)) → SettingsRepository.refresh(accountType LINKED). 토큰은 바꾸지 않는다. 다른 회원과 연동된 소셜 계정
 * (ALREADY_MEMBER)·네트워크 실패면 자격 증명을 그대로 두고 그 실패를 돌려준다. ALREADY_LINKED는 이미 연동한 회원이라는 뜻이므로
 * (서버는 자격 증명 해시를 이미 지웠다) 기기 자격 증명도 지우고 설정 사본을 갱신한 뒤 그 거부를 돌려준다(A1 설계 갱신 요청 1 총괄 결정).
 * 자격 증명을 지우지 못하면 그 실패를 알린다. 설정 사본 갱신 실패는 연동을 막지 않는다.
 */
class LinkSocialAccountUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(token: ProviderToken): Outcome<Unit> {
        val linked = account.linkSocialAccount(token)
        val isAlreadyLinked = linked is Outcome.Failure && linked.error == AppError.Rejected(ALREADY_LINKED)
        if (linked is Outcome.Failure && !isAlreadyLinked) return linked
        val cleared = session.clearGuestCredential()
        settings.refresh()
        return if (isAlreadyLinked) linked else cleared
    }

    private companion object {
        /** 서버 ReasonCode: 이미 연동한 회원(F.2.3). */
        const val ALREADY_LINKED = "ALREADY_LINKED"
    }
}
