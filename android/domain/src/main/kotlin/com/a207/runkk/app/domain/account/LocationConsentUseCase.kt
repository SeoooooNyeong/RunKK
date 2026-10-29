package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 러닝 시작 전 위치 수집 동의 확인과 주기(D-267, FR-031). 자유런 카드(홈), 랜덤 매칭·친구 초대(배틀런 화면), 참가하기가
 * 시작 전에 [isGranted]를 보고 거짓이면 위치 동의 시트를 띄우고, "동의하고 시작"이면 [give]를 부른다.
 */
class LocationConsentUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val settings: SettingsRepository
) {
    /** 설정 사본의 위치 수집 동의. */
    fun isGranted(): Boolean = settings.locationConsented()

    /** 위치 수집 동의(PUT /me/consents/LOCATION) 뒤 설정 사본을 다시 읽는다. 동의가 실패하면 사본을 건드리지 않는다. */
    suspend fun give(): Outcome<Unit> {
        val given = account.giveConsent(LOCATION)
        if (given is Outcome.Failure) return given
        return settings.refresh()
    }

    private companion object {
        /** 서버 ConsentItem 이름. */
        const val LOCATION = "LOCATION"
    }
}
