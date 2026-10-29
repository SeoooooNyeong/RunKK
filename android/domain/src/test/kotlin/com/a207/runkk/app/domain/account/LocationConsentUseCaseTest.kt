package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 러닝 시작 전 위치 동의(D-267, FR-031). 시험 설계 10-06 TC-9684. */
class LocationConsentUseCaseTest {
    private val log = CallLog()
    private val settings = FakeSettingsRepository(SettingsSnapshot.SAFE, log)
    private val account = FakeAccountRepository(log)
    private val consent = LocationConsentUseCase(account, settings)

    @Test
    fun `TC-9684 isGranted는 설정 사본 값이다`() {
        assertFalse(consent.isGranted())
        settings.snapshot = SettingsSnapshot.SAFE.copy(isLocationConsented = true)
        assertTrue(consent.isGranted())
    }

    @Test
    fun `TC-9684 동의는 giveConsent LOCATION 뒤 사본 갱신 순서로 한 번씩`() = runTest {
        assertEquals(Outcome.Success(Unit), consent.give())
        assertEquals(listOf("account.giveConsent:LOCATION", "settings.refresh"), log.calls)
    }

    @Test
    fun `TC-9684 동의가 실패하면 실패를 돌려주고 사본은 갱신하지 않는다`() = runTest {
        account.failures["giveConsent:LOCATION"] = AppError.Network
        assertEquals(Outcome.Failure(AppError.Network), consent.give())
        assertEquals(listOf("account.giveConsent:LOCATION"), log.calls)
    }
}
