package com.a207.runkk.app.ui.me

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.LogoutUseCase
import com.a207.runkk.app.domain.account.WithdrawUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** 마이·프로필 편집·설정·탈퇴 ViewModel: 상태 전이와 오류 표시. */
class MeViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val session = FakeSessionRepository()
    private val settingsCopy = FakeSettingsRepository()

    @Test
    fun `마이는 본인 프로필을 읽는다`() {
        val vm = MeViewModel(account, LogoutUseCase(account, session, settingsCopy))
        assertEquals(sampleProfile, vm.state.value.profile)
        assertEquals(listOf("profile:${AccountRepository.SELF}"), account.calls)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `프로필이 셀카 얼굴이면 부위 그림을 받아 원 안에 그린다`() {
        val layer = com.a207.runkk.app.core.model.FaceLayer(com.a207.runkk.app.core.model.FacePart.EYES, byteArrayOf(1))
        account.profile = sampleProfile.copy(faceKey = "selfie:face/7/s1")
        account.faceResult = com.a207.runkk.app.core.model.Outcome.Success(listOf(layer))
        val vm = MeViewModel(account, LogoutUseCase(account, session, settingsCopy))
        assertEquals(listOf(layer), vm.state.value.faceLayers)
        assertEquals(listOf("profile:${AccountRepository.SELF}", "face:selfie:face/7/s1"), account.calls)
    }

    @Test
    fun `프로필을 못 읽으면 오류를 보이고 다시 시도한다`() {
        account.failures["profile:0"] = AppError.Network
        val vm = MeViewModel(account, LogoutUseCase(account, session, settingsCopy))
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        account.failures.clear()
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(sampleProfile, vm.state.value.profile)
    }

    @Test
    fun `로그아웃하면 로그인 화면으로 간다(FR-002)`() = runTest {
        val vm = MeViewModel(account, LogoutUseCase(account, session, settingsCopy))
        vm.events.test {
            vm.onLogoutConfirmed()
            assertEquals(MeEvent.LoggedOut, awaitItem())
        }
        assertTrue("logout" in account.calls)
    }

    @Test
    fun `프로필 편집은 현재 값을 채우고 바꾼 값을 저장한다(FR-007)`() = runTest {
        val vm = ProfileEditViewModel(account)
        assertEquals("달리는곰", vm.state.value.nickname)
        assertEquals(setOf("SOCIAL_RUN", "EVENING"), vm.state.value.tags)
        vm.onNicknameChange("빠른곰")
        vm.onTagToggled("SOLO_RUN")
        vm.events.test {
            vm.onSave()
            assertEquals(ProfileEditEvent.Saved, awaitItem())
        }
        assertEquals(Triple("빠른곰", "퇴근 뒤 한강", setOf("SOLO_RUN", "EVENING")), account.lastEdit)
    }

    @Test
    fun `프로필 저장이 거부되면 사유를 보이고 머문다`() {
        account.failures["editProfile"] = AppError.Rejected("NICKNAME_INVALID")
        val vm = ProfileEditViewModel(account)
        vm.onSave()
        assertEquals(RunkkErrorText.rejected("NICKNAME_INVALID"), vm.state.value.error)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun `설정 값을 바꾸면 변경 흐름을 부르고 화면에 바로 반영한다`() {
        val vm = SettingsViewModel(account, ChangeSettingUseCase(account, settingsCopy))
        assertEquals("true", vm.state.value.value(SettingKeys.RUN_VOICE))
        vm.setValue(SettingKeys.RUN_VOICE, "false")
        assertEquals("false", vm.state.value.value(SettingKeys.RUN_VOICE))
        assertTrue("changeSetting:RUN_SETTINGS.voice=false" in account.calls)
        assertEquals(1, settingsCopy.refreshes)
    }

    @Test
    fun `설정 변경이 실패하면 이전 값으로 되돌리고 알린다`() {
        account.failures["changeSetting:UNIT=MI"] = AppError.Network
        val vm = SettingsViewModel(account, ChangeSettingUseCase(account, settingsCopy))
        vm.setValue(SettingKeys.UNIT, "MI")
        assertEquals("KM", vm.state.value.value(SettingKeys.UNIT))
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.message)
    }

    @Test
    fun `동의 주기·철회는 동의 연산으로 보낸다(D-36)`() {
        val vm = SettingsViewModel(account, ChangeSettingUseCase(account, settingsCopy))
        vm.setConsent("HEART_RATE", true)
        vm.setConsent("LOCATION", false)
        assertTrue("giveConsent:HEART_RATE" in account.calls)
        assertTrue("withdrawConsent:LOCATION" in account.calls)
        assertEquals(true, vm.state.value.consent("HEART_RATE"))
        assertEquals(false, vm.state.value.consent("LOCATION"))
    }

    @Test
    fun `탈퇴는 확인한 뒤에만 보내고 끝나면 로그인 화면으로 간다(FR-020)`() = runTest {
        val pending = FakePendingRunRepository()
        val vm = WithdrawViewModel(WithdrawUseCase(account, pending, settingsCopy, session))
        vm.onWithdrawConfirmed()
        assertFalse("withdraw" in account.calls)
        vm.onAcknowledgedChange(true)
        vm.events.test {
            vm.onWithdrawConfirmed()
            assertEquals(WithdrawEvent.Withdrawn, awaitItem())
        }
        assertTrue(pending.cleared)
    }

    @Test
    fun `크루장 탈퇴 거부는 양도 안내를 보인다(FR-024)`() {
        account.failures["withdraw"] = AppError.Rejected("LEADER_MUST_TRANSFER")
        val vm = WithdrawViewModel(WithdrawUseCase(account, FakePendingRunRepository(), settingsCopy, session))
        vm.onAcknowledgedChange(true)
        vm.onWithdrawConfirmed()
        assertEquals(RunkkErrorText.rejected("LEADER_MUST_TRANSFER"), vm.state.value.error)
        assertFalse(vm.state.value.isWithdrawing)
    }

    @Test
    fun `한 설정이 실패해도 그사이 바꾼 다른 설정은 남는다`() {
        account.failures["changeSetting:UNIT=MI"] = AppError.Network
        val vm = SettingsViewModel(account, ChangeSettingUseCase(account, settingsCopy))
        vm.setValue(SettingKeys.RUN_VOICE, "false")
        vm.setValue(SettingKeys.UNIT, "MI")
        assertEquals("KM", vm.state.value.value(SettingKeys.UNIT))
        assertEquals("false", vm.state.value.value(SettingKeys.RUN_VOICE))
    }
}
