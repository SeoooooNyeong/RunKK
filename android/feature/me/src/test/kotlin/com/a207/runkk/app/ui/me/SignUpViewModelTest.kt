package com.a207.runkk.app.ui.me

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
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
    fun `가입은 닉네임 없이 동의만 보내고 홈으로 간다(D-90)`() = runTest {
        agreeRequired()
        vm.events.test {
            vm.onSubmit(isGuest = false)
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertEquals(SignUpForm(nickname = null, consents = vm.state.value.consents), account.lastForm)
        assertTrue(session.loggedIn.value)
    }

    @Test
    fun `필수 동의가 빠지면 보내지 않는다`() {
        vm.onSubmit(isGuest = false)
        vm.onSubmit(isGuest = true)
        assertNull(account.lastForm)
        assertNull(account.lastGuestConsents)
    }

    @Test
    fun `가입 거부는 화면에 남기고, 가입 시간이 지났으면 로그인부터 다시 하게 한다`() {
        agreeRequired()
        account.failures["signUp"] = AppError.Rejected("UNDER_14")
        vm.onSubmit(isGuest = false)
        assertEquals(RunkkErrorText.rejected("UNDER_14"), vm.state.value.submitError)
        assertTrue(vm.state.value.canRetrySubmit)
        account.failures["signUp"] = AppError.Rejected("SIGN_UP_TICKET_INVALID")
        vm.onSubmit(isGuest = false)
        assertFalse(vm.state.value.canRetrySubmit)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `게스트로 시작하면 같은 동의를 보내고 자료 유실 고지를 먼저 보인다(FR-197·FR-199)`() = runTest {
        agreeRequired()
        vm.events.test {
            vm.onSubmit(isGuest = true)
            expectNoEvents()
            assertTrue(vm.state.value.isGuestNoticeVisible)
            assertEquals(vm.state.value.consents, account.lastGuestConsents)
            assertTrue(session.hasGuestCredential())
            vm.onGuestNoticeConfirmed()
            assertEquals(SignUpEvent.Completed, awaitItem())
        }
        assertFalse(vm.state.value.isGuestNoticeVisible)
    }

    @Test
    fun `게스트 고지에서 계정 연동을 고르면 설정의 연동으로 간다(FR-199 제안)`() = runTest {
        agreeRequired()
        vm.onSubmit(isGuest = true)
        vm.events.test {
            vm.onGuestNoticeLinkTapped()
            assertEquals(SignUpEvent.CompletedToLink, awaitItem())
        }
    }

    @Test
    fun `게스트 접속지 한도(429)는 잠시 뒤 다시 하라고 알린다`() {
        agreeRequired()
        account.guestResult = Outcome.Failure(AppError.Server(429))
        vm.onSubmit(isGuest = true)
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
