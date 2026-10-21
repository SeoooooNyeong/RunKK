package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/**
 * 설정 변경 요청(FR-008~FR-017). 갈래마다 서버 연산이 다르다(D-36): 일반 설정은 PATCH /me/settings,
 * 동의는 PUT·DELETE /me/consents/{item}. item 값은 서버 SettingItem·ConsentItem 상수 이름이다.
 */
sealed interface SettingChangeRequest {
    data class Change(val item: String, val value: String) : SettingChangeRequest

    data class GiveConsent(val item: String) : SettingChangeRequest

    data class WithdrawConsent(val item: String) : SettingChangeRequest
}
