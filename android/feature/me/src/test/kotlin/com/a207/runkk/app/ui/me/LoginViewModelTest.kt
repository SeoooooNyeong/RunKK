package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.LoginUseCase
import com.a207.runkk.app.domain.account.ProviderType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val kakao = FakeSocialLoginClient(ProviderType.KAKAO)
    private val google = FakeSocialLoginClient(ProviderType.GOOGLE)
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun viewModel() =
        LoginViewModel(setOf(kakao, google), LoginUseCase(account, FakeSessionRepository(), FakeSettingsRepository()))

    @Test
    fun `가입된 회원은 로그인 뒤 홈으로 간다`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onProviderTapped(ProviderType.KAKAO, context)
            assertEquals(LoginEvent.LoggedIn, awaitItem())
        }
        assertEquals(1, kakao.calls)
        assertEquals(listOf("login:KAKAO"), account.calls)
        assertNull(vm.state.value.inProgress)
    }

    @Test
    fun `가입 전 회원은 가입 단계로 간다(FR-001 가입필요)`() = runTest {
        account.loginResult = Outcome.Failure(AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED))
        val vm = viewModel()
        vm.events.test {
            vm.onProviderTapped(ProviderType.GOOGLE, context)
            assertEquals(LoginEvent.SignUpRequired, awaitItem())
        }
        assertEquals(1, google.calls)
    }

    @Test
    fun `제공자 실패는 사유를 보이고 다시 누를 수 있다`() = runTest {
        account.loginResult = Outcome.Failure(AppError.Rejected("PROVIDER_UNAVAILABLE"))
        val vm = viewModel()
        vm.onProviderTapped(ProviderType.KAKAO, context)
        assertEquals("로그인 제공자 연결 실패. 잠시 뒤 다시 시도해 주세요", vm.state.value.error)
        assertNull(vm.state.value.inProgress)
        vm.onErrorShown()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `SDK 로그인이 실패하면 서버를 부르지 않는다`() = runTest {
        kakao.result = Outcome.Failure(AppError.Rejected("LOGIN_CANCELLED"))
        val vm = viewModel()
        vm.onProviderTapped(ProviderType.KAKAO, context)
        assertEquals("로그인을 취소했어요", vm.state.value.error)
        assertEquals(emptyList(), account.calls)
    }
}
