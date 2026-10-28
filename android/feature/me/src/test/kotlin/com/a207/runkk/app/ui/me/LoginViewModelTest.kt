package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
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

    /** 화면이 하는 순서: 창구를 받아 화면 코루틴에서 SDK 로그인을 띄우고 토큰을 넘긴다(AN5-D-11). */
    private suspend fun LoginViewModel.tap(provider: ProviderType) {
        onLoginStart(provider)?.let { client -> onLoginToken(client.signIn(context)) }
    }

    @Test
    fun `AN5-D-11 진행 중에는 다시 시작하지 않고 화면이 사라져 끊기면 진행 표시를 푼다`() = runTest {
        val vm = viewModel()

        assertEquals(kakao, vm.onLoginStart(ProviderType.KAKAO))
        assertEquals(ProviderType.KAKAO, vm.state.value.inProgress)
        assertNull(vm.onLoginStart(ProviderType.GOOGLE), "진행 중에는 다시 시작하지 않는다")
        vm.onLoginCancelled()

        assertNull(vm.state.value.inProgress)
        assertEquals(emptyList(), account.calls)
    }

    @Test
    fun `가입된 회원은 로그인 뒤 홈으로 간다`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.tap(ProviderType.KAKAO)
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
            vm.tap(ProviderType.GOOGLE)
            assertEquals(LoginEvent.SignUpRequired, awaitItem())
        }
        assertEquals(1, google.calls)
    }

    @Test
    fun `제공자 실패는 사유를 보이고 다시 누를 수 있다`() = runTest {
        account.loginResult = Outcome.Failure(AppError.Rejected("PROVIDER_UNAVAILABLE"))
        val vm = viewModel()
        vm.tap(ProviderType.KAKAO)
        assertEquals(RunkkErrorText.rejected("PROVIDER_UNAVAILABLE"), vm.state.value.error)
        assertNull(vm.state.value.inProgress)
        vm.onErrorShown()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `SDK 로그인이 실패하면 서버를 부르지 않는다`() = runTest {
        kakao.result = Outcome.Failure(AppError.Rejected("LOGIN_CANCELLED"))
        val vm = viewModel()
        vm.tap(ProviderType.KAKAO)
        assertEquals(RunkkErrorText.rejected("LOGIN_CANCELLED"), vm.state.value.error)
        assertEquals(emptyList(), account.calls)
    }
}
