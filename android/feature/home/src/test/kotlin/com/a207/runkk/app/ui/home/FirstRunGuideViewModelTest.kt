package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.FirstRunGuide
import com.a207.runkk.app.domain.settings.GuideCopy
import com.a207.runkk.app.domain.settings.GuideRemote
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/** 첫 사용 가이드 진행(D-277, FR-231). 시험 설계 10-06 TC-9713~TC-9715. */
class FirstRunGuideViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val remote = FakeGuideRemote()
    private val session = FakeSessionRepository()

    private fun viewModel(guideRemote: GuideRemote = remote) =
        FirstRunGuideViewModel(FirstRunGuide(settings, guideRemote), session)

    @Test
    fun `처음 로그인한 러너에게 1단계부터 보인다`() {
        val vm = viewModel()
        assertEquals(FirstRunGuideUiState(isVisible = true, index = 0), vm.state.value)
    }

    @Test
    fun `이미 끝냈으면 서버를 묻지 않고 보이지 않는다`() {
        settings.guide = GuideCopy.SEEN
        assertFalse(viewModel().state.value.isVisible)
        assertEquals(0, remote.seenCalls)
    }

    @Test
    fun `로그인하지 않았으면 보이지 않고 로그인하면 그때 판정한다`() {
        session.loggedIn.value = false
        val vm = viewModel()
        assertFalse(vm.state.value.isVisible)
        session.loggedIn.value = true
        assertTrue(vm.state.value.isVisible)
    }

    @Test
    fun `TC-9713 다음은 한 단계씩 넘기고 마지막 단계의 시작하기가 끝낸다`() {
        val vm = viewModel()
        repeat(5) { vm.onNext(stepCount = 6) }
        assertEquals(5, vm.state.value.index)
        assertTrue(vm.state.value.isLastOf(6))
        assertEquals(0, remote.markCalls)
        vm.onNext(stepCount = 6)
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, remote.markCalls)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `TC-9715 건너뛰기도 끝냄으로 저장하고 다시 보이지 않는다`() {
        val vm = viewModel()
        vm.onSkip()
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, remote.markCalls)
        assertFalse(viewModel().state.value.isVisible)
    }

    @Test
    fun `TC-9715 서버 저장이 실패해도 닫히고 기기 사본은 끝냄이다`() {
        val failing = object : GuideRemote by remote {
            override suspend fun markSeen(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        }
        val vm = viewModel(failing)
        vm.onSkip()
        assertFalse(vm.state.value.isVisible)
        assertEquals(GuideCopy.SEEN_UNSENT, settings.guide)
    }
}
