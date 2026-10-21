package com.a207.runkk.app.ui.me

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.LoginUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val vm = SignUpViewModel(LoginUseCase(account, FakeSessionRepository(), FakeSettingsRepository()), account)

    private fun agreeRequired() = SignUpConsents.items.filter {
        it.required
    }.forEach { vm.onConsentChange(it.item, true) }

    @Test
    fun `필수 동의 4개가 모두 있어야 다음 단계로 간다(FR-003·FR-004)`() {
        assertFalse(vm.state.value.canProceed)
        vm.onConsentChange("TERMS", true)
        vm.onNext()
        assertEquals(SignUpStep.CONSENT, vm.state.value.step)
        agreeRequired()
        assertTrue(vm.state.value.canProceed)
        vm.onNext()
        assertEquals(SignUpStep.PROFILE, vm.state.value.step)
    }

    @Test
    fun `전체 동의는 선택 동의까지 켜고 끈다`() {
        vm.onAllAgreedChange(true)
        assertTrue(vm.state.value.consents.values.all { it })
        assertTrue(vm.state.value.allAgreed)
        vm.onConsentChange("MARKETING", false)
        assertFalse(vm.state.value.allAgreed)
        vm.onAllAgreedChange(false)
        assertTrue(vm.state.value.consents.values.none { it })
    }

    @Test
    fun `닉네임은 비우거나 2~20자일 때만 넘어가고 태그는 하나 이상`() {
        agreeRequired()
        vm.onNext()
        vm.onNicknameChange("곰")
        assertFalse(vm.state.value.canProceed)
        vm.onNicknameChange("")
        assertTrue(vm.state.value.canProceed)
        vm.onNicknameChange("달리는곰")
        vm.onNext()
        assertEquals(SignUpStep.TAGS, vm.state.value.step)
        assertFalse(vm.state.value.canProceed)
        vm.onTagToggled("SOLO")
        vm.onTagToggled("SOCIAL")
        assertEquals(setOf("SOCIAL"), vm.state.value.tags)
        vm.onNext()
        assertEquals(SignUpStep.AVATAR, vm.state.value.step)
        vm.onBack()
        assertEquals(SignUpStep.TAGS, vm.state.value.step)
    }

    @Test
    fun `가입하면 입력값을 그대로 보내고 아바타 만들기로 간다`() = runTest {
        agreeRequired()
        vm.onConsentChange("LLM_TRANSFER", true)
        vm.onNext()
        vm.onNicknameChange("달리는곰")
        vm.onBirthYearChange(1998)
        vm.onGenderChange(Gender.FEMALE)
        vm.onIntroChange("퇴근 뒤 한강")
        vm.onVisibilityChange("CREW")
        vm.onNext()
        vm.onTagToggled("EVENING")
        vm.onNext()
        vm.events.test {
            vm.onSubmit(createAvatar = true)
            assertEquals(SignUpEvent.Completed(createAvatar = true), awaitItem())
        }
        val form = requireNotNull(account.lastForm)
        assertEquals("달리는곰", form.nickname)
        assertEquals(1998, form.birthYear)
        assertEquals(Gender.FEMALE, form.gender)
        assertEquals("CREW", form.recordVisibility)
        assertEquals(setOf("EVENING"), form.tags)
        assertEquals(true, form.consents["LLM_TRANSFER"])
        assertEquals(false, form.consents["MARKETING"])
    }

    @Test
    fun `빈 닉네임·성별 미선택은 계정 이름·밝히지 않음으로 보낸다`() = runTest {
        agreeRequired()
        vm.onNext()
        vm.onNext()
        vm.onTagToggled("SOLO")
        vm.onNext()
        vm.onSubmit(createAvatar = false)
        val form = requireNotNull(account.lastForm)
        assertEquals(null, form.nickname)
        assertEquals(Gender.UNDISCLOSED, form.gender)
        assertEquals(null, form.intro)
    }

    @Test
    fun `만 14세 미만 거부는 동의 단계로 돌아가 사유를 보인다`() = runTest {
        account.failures["signUp"] = AppError.Rejected("UNDER_14")
        agreeRequired()
        vm.onNext()
        vm.onNext()
        vm.onTagToggled("SOLO")
        vm.onNext()
        vm.onSubmit(createAvatar = false)
        assertEquals(SignUpStep.CONSENT, vm.state.value.step)
        assertEquals("만 14세 이상만 가입할 수 있음", vm.state.value.error)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `닉네임 중복은 프로필 단계로 돌아간다`() = runTest {
        account.failures["signUp"] = AppError.Rejected("NICKNAME_TAKEN")
        agreeRequired()
        vm.onNext()
        vm.onNicknameChange("달리는곰")
        vm.onNext()
        vm.onTagToggled("SOLO")
        vm.onNext()
        vm.onSubmit(createAvatar = true)
        assertEquals(SignUpStep.PROFILE, vm.state.value.step)
    }

    @Test
    fun `약관 보기는 본문을 띄우고 주소면 밖에서 연다`() = runTest {
        vm.onViewLegal("TERMS")
        assertEquals("약관 본문", vm.state.value.legal?.body)
        vm.onLegalDismissed()
        assertEquals(null, vm.state.value.legal)
        account.legal = "https://runkk.example/legal/privacy"
        vm.events.test {
            vm.onViewLegal("PRIVACY")
            assertEquals(SignUpEvent.OpenUrl("https://runkk.example/legal/privacy"), awaitItem())
        }
    }
}
