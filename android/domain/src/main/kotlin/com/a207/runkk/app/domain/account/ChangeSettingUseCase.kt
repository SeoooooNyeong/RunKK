package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 설정 변경·동의 주기·철회(FR-008~FR-017): AccountRepository.changeSetting / giveConsent / withdrawConsent →
 * SettingsRepository.refresh. 서버 변경이 실패하면 사본을 갱신하지 않는다.
 * 동의 철회가 사본에 늦게 반영되면 워치 심박 수집이 계속될 수 있어(NFR-023) 갱신 실패도 그대로 알린다.
 */
class ChangeSettingUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(change: SettingChangeRequest): Outcome<Unit> {
        val changed =
            when (change) {
                is SettingChangeRequest.Change -> account.changeSetting(change.item, change.value)
                is SettingChangeRequest.GiveConsent -> account.giveConsent(change.item)
                is SettingChangeRequest.WithdrawConsent -> account.withdrawConsent(change.item)
            }
        if (changed is Outcome.Failure) return changed
        return settings.refresh()
    }
}
