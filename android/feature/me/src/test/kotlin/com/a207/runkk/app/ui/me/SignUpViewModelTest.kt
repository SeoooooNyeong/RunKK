package com.a207.runkk.app.ui.me

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.account.SignUpUseCase
import com.a207.runkk.app.domain.account.StartAsGuestUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** 동의 화면 하나(가입·게스트 공용, FR-003·FR-004·FR-028·FR-197·FR-199, D-90, D-159). */
class SignUpViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val session = FakeSessionRepository()
    private val settings = FakeSettingsRepository()
    private val vm =
        SignUpViewModel(
            SignUpUseCase(account, session, settings),
            StartAsGuestUseCase(account, session, settings),
            account
        )

    private fun agreeRequired() =
        SignUpConsents.items.filter { it.isRequired }.forEach { vm.onConsentChange(it.item, true) }

    /** 필수 동의 → 다음(성별 단계) → [gender] 고르기. */
    private fun toGenderStep(gender: Gender? = Gender.MALE) {
        agreeRequired()
        vm.onConsentNext()
        gender?.let(vm::onGenderSelect)
    }

    @Test
    fun `필수 동의 4개가 모두 있어야 시작할 수 있고 선택 동의는 기본 꺼짐이다(FR-003·FR-004)`() {
        assertFalse(vm.state.value.canSubmit)
        assertTrue(vm.state.value.consents.values.none { it })
        vm.onConsentChange("TERMS", true)
        assertFalse(vm.state.value.canSubmit)
        agreeRequired()
        assertTrue(vm.state.value.canSubmit)
        assertEquals(
            listOf("AGE_14_PLUS", "TERMS", "PRIVACY", "LOCATION", "MARKETING", "LLM_TRANSFER"),
            SignUpConsents.items.map {
                it.item
            }
        )
    }

    @Test
    fun `전체 동의는 선택 동의까지 켜고 끈다`() {
        vm.onAllAgreedChange(true)
        assertTrue(vm.state.value.isAllAgreed)
        vm.onAllAgreedChange(false)
        assertTrue(vm.state.value.consents.values.none { it })
    }

    @Test
    fun `동의 화면의 시작하기는 가입하지 않고 성별 단계로 간다(S15P31A207-300)`() {
        vm.onConsentNext()
        assertEquals(SignUpStep.CONSENT, vm.state.value.step, "필수 동의 전에는 넘어가지 않는다")
        agreeRequired()
        vm.onConsentNext()
        assertEquals(SignUpStep.GENDER, vm.state.value.step)
        assertNull(vm.state.value.gender, "처음에는 아무것도 고르지 않는다")
        assertNull(account.lastForm)
        assertNull(account.lastGuestConsents)
    }

    @Test
    fun `성별을 남자나 여자로 골라야 시작할 수 있다(S15P31A207-300)`() {
        toGenderStep(gender = null)
        assertFalse(vm.state.value.canStart)
        vm.onStart(isGuest = false)
        assertNull(account.lastForm, "고르기 전에는 보내지 않는다")
        vm.onGenderSelect(Gender.UNDISCLOSED)
        assertNull(vm.state.value.gender, "밝히지 않음은 고를 수 없다")
        vm.onGenderSelect(Gender.FEMALE)
        assertTrue(vm.state.value.canStart)
        vm.onGenderSelect(Gender.MALE)
        assertEquals(Gender.MALE, vm.state.value.gender)
    }

    @Test
    fun `가입은 닉네임 없이 동의만 보내고 고른 성별을 프로필에 저장한 뒤 홈으로 간다(D-90, S15P31A207-300)`() = runTest {
        toGenderStep(Gender.FEMALE)
        vm.events.test {
            vm.onStart(isGuest = false)
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertEquals(SignUpForm(nickname = null, consents = vm.state.value.consents), account.lastForm)
        assertTrue(session.loggedIn.value)
        assertEquals(listOf("signUp", "profile:0", "editProfile"), account.calls, "가입 → 공개 여부 읽기 → 성별 저장")
        // 닉네임·소개는 그대로 두고(null) 공개 여부는 서버 값을 다시 보낸다
        assertEquals(
            ProfileEdit(
                nickname = null,
                intro = null,
                isAgePublic = true,
                isGenderPublic = false,
                gender = Gender.FEMALE
            ),
            account.lastEdit
        )
    }

    @Test
    fun `공개 여부를 읽지 못해도 새 계정 기본(비공개)으로 성별을 저장한다`() = runTest {
        account.failures["profile:0"] = AppError.Network
        toGenderStep(Gender.MALE)
        vm.events.test {
            vm.onStart(isGuest = false)
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertEquals(
            ProfileEdit(
                nickname = null,
                intro = null,
                isAgePublic = false,
                isGenderPublic = false,
                gender = Gender.MALE
            ),
            account.lastEdit
        )
    }

    @Test
    fun `성별 저장이 실패하면 단계에 남아 안내하고 다시 시작하면 가입 없이 성별만 저장한다`() = runTest {
        toGenderStep(Gender.MALE)
        account.failures["editProfile"] = AppError.Network
        vm.events.test {
            vm.onStart(isGuest = false)
            expectNoEvents()
            val s = vm.state.value
            assertEquals(SignUpStep.GENDER, s.step)
            assertEquals(RunkkErrorText.of(AppError.Network), s.submitError)
            assertTrue(s.canRetrySubmit)
            assertTrue(s.isAccountCreated)
            assertTrue(s.canStart, "다시 시작할 수 있다")
            assertFalse(s.isSubmitting)

            // 계정은 이미 있으니 뒤로 가서 동의를 다시 보내지 않는다
            vm.onBack()
            assertEquals(SignUpStep.GENDER, vm.state.value.step)

            account.failures.remove("editProfile")
            account.calls.clear()
            vm.onGenderSelect(Gender.FEMALE)
            vm.onStart(isGuest = false)
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertEquals(listOf("profile:0", "editProfile"), account.calls, "가입은 한 번만")
        assertEquals(Gender.FEMALE, account.lastEdit?.gender)
        assertNull(vm.state.value.submitError)
    }

    @Test
    fun `성별 단계에서 뒤로 가면 고른 동의를 그대로 둔 채 동의 화면으로 돌아가고, 동의 화면의 뒤로는 나간다`() = runTest {
        toGenderStep(Gender.MALE)
        vm.onConsentChange("MARKETING", true)
        val consents = vm.state.value.consents
        vm.events.test {
            vm.onBack()
            expectNoEvents()
            assertEquals(SignUpStep.CONSENT, vm.state.value.step)
            assertEquals(consents, vm.state.value.consents)
            vm.onBack()
            assertEquals(SignUpEvent.Exit, awaitItem())
        }
    }

    @Test
    fun `필수 동의가 빠지면 보내지 않는다`() {
        vm.onStart(isGuest = false)
        vm.onStart(isGuest = true)
        assertNull(account.lastForm)
        assertNull(account.lastGuestConsents)
    }

    @Test
    fun `가입 거부는 화면에 남기고, 가입 시간이 지났으면 로그인부터 다시 하게 한다`() {
        agreeRequired()
        vm.onConsentNext()
        vm.onGenderSelect(Gender.MALE)
        account.failures["signUp"] = AppError.Rejected("UNDER_14")
        vm.onStart(isGuest = false)
        assertEquals(RunkkErrorText.rejected("UNDER_14"), vm.state.value.submitError)
        assertTrue(vm.state.value.canRetrySubmit)
        assertFalse(vm.state.value.isAccountCreated)
        account.failures["signUp"] = AppError.Rejected("SIGN_UP_TICKET_INVALID")
        vm.onStart(isGuest = false)
        assertFalse(vm.state.value.canRetrySubmit)
        assertFalse(vm.state.value.isSubmitting)
        assertNull(account.lastEdit, "가입이 안 되면 성별을 저장하지 않는다")
    }

    @Test
    fun `게스트로 시작하면 같은 동의를 보내고 성별을 저장한 뒤 자료 유실 고지를 먼저 보인다(FR-197·FR-199)`() = runTest {
        toGenderStep(Gender.FEMALE)
        vm.events.test {
            vm.onStart(isGuest = true)
            expectNoEvents()
            assertTrue(vm.state.value.isGuestNoticeVisible)
            assertEquals(account.profile.displayName, vm.state.value.guestNickname)
            assertEquals(vm.state.value.consents, account.lastGuestConsents)
            assertTrue(session.hasGuestCredential())
            assertEquals(listOf("startAsGuest", "profile:0", "editProfile"), account.calls)
            assertEquals(Gender.FEMALE, account.lastEdit?.gender)
            vm.onGuestNoticeConfirmed()
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertFalse(vm.state.value.isGuestNoticeVisible)
    }

    @Test
    fun `게스트 고지에서 계정 연동을 고르면 설정의 연동으로 간다(FR-199 제안)`() = runTest {
        toGenderStep()
        vm.onStart(isGuest = true)
        vm.events.test {
            vm.onGuestNoticeLinkTapped()
            assertEquals(SignUpEvent.CompletedToLink, awaitItem())
        }
    }

    @Test
    fun `게스트도 성별 저장이 실패하면 고지 없이 단계에 남고 다시 시작하면 성별만 저장한다`() = runTest {
        toGenderStep(Gender.MALE)
        account.failures["editProfile"] = AppError.Server(500)
        vm.onStart(isGuest = true)
        assertFalse(vm.state.value.isGuestNoticeVisible)
        assertTrue(vm.state.value.isAccountCreated)
        assertEquals(RunkkErrorText.of(AppError.Server(500)), vm.state.value.submitError)
        account.failures.remove("editProfile")
        account.calls.clear()
        vm.onStart(isGuest = true)
        assertTrue(vm.state.value.isGuestNoticeVisible)
        assertEquals(listOf("profile:0", "editProfile"), account.calls, "게스트 계정을 또 만들지 않는다")
    }

    @Test
    fun `게스트 접속지 한도(429)는 잠시 뒤 다시 하라고 알린다`() {
        toGenderStep()
        account.guestResult = Outcome.Failure(AppError.Server(429))
        vm.onStart(isGuest = true)
        assertEquals(UiText.Res(R.string.me_guest_too_many), vm.state.value.submitError)
        assertFalse(vm.state.value.isGuestNoticeVisible)
        assertFalse(session.loggedIn.value)
    }

    @Test
    fun `약관 보기는 본문이면 대화상자, 주소면 밖에서 연다(FR-018)`() = runTest {
        vm.onViewLegal("TERMS")
        assertEquals("약관 본문", vm.state.value.legal?.body)
        vm.onLegalDismissed()
        account.legal = "https://runkk.example/terms"
        vm.events.test {
            vm.onViewLegal("TERMS")
            assertEquals(SignUpEvent.OpenUrl("https://runkk.example/terms"), awaitItem())
        }
    }

    @Test
    fun `AN5-D-15 고지 문서가 http 주소면 밖에서 열지 않고 본문으로 보이며 열기 실패는 안내한다`() = runTest {
        account.legal = "http://runkk.example/terms"
        vm.events.test {
            vm.onViewLegal("TERMS")
            expectNoEvents()
        }
        assertEquals("http://runkk.example/terms", vm.state.value.legal?.body, "암호화 없는 주소는 열지 않는다")

        vm.onLegalOpenFailed()

        assertEquals(UiText.Res(R.string.me_legal_open_failed), vm.state.value.error)
    }
}
