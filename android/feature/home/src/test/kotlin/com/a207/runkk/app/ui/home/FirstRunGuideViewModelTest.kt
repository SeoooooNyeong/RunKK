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

/**
 * 첫 사용 가이드 진행([임시:FR-성-35], [임시:FR-성-36], [임시:D-성-297], D-277): 시작 카드 → 단계(이전·다음) → 끝. 첫 안내의 끝은 본
 * 것으로 저장하고, 다시 보기는 1단계부터 보이되 저장하지 않는다. 시험 설계 10-06 TC-9713~TC-9715.
 */
class FirstRunGuideViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val remote = FakeGuideRemote()
    private val session = FakeSessionRepository()

    private fun viewModel(guideRemote: GuideRemote = remote) =
        FirstRunGuideViewModel(FirstRunGuide(settings, guideRemote), session)

    @Test
    fun `처음 로그인한 러너에게 처음에는 시작 카드가 보인다`() {
        val vm = viewModel()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.START_CARD, index = 0, isReplay = false), vm.state.value)
        assertTrue(vm.state.value.isVisible)
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
        assertEquals(GuidePhase.START_CARD, vm.state.value.phase)
    }

    @Test
    fun `둘러보기는 단계 0에서 시작한다`() {
        val vm = viewModel()
        vm.onStartTour()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 0), vm.state.value)
        assertEquals(0, remote.markCalls, "아직 끝내지 않았다")
    }

    @Test
    fun `시작 카드에서는 다음과 이전이 아무것도 하지 않는다`() {
        val vm = viewModel()
        vm.onNext(stepCount = 4)
        vm.onPrevious()
        assertEquals(GuidePhase.START_CARD, vm.state.value.phase)
        assertEquals(0, vm.state.value.index)
    }

    @Test
    fun `TC-9713 다음은 한 단계씩 넘기고 마지막 단계의 시작하기가 저장하고 닫는다`() {
        val vm = viewModel()
        vm.onStartTour()
        repeat(3) { vm.onNext(stepCount = 4) }
        assertEquals(3, vm.state.value.index)
        assertTrue(vm.state.value.isLastOf(4))
        assertEquals(0, remote.markCalls)
        vm.onNext(stepCount = 4)
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, remote.markCalls)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `닫힌 기능이 있어 3단계면 셋째 단계의 시작하기가 끝낸다`() {
        val vm = viewModel()
        vm.onStartTour()
        repeat(2) { vm.onNext(stepCount = 3) }
        assertTrue(vm.state.value.isLastOf(3))
        vm.onNext(stepCount = 3)
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, remote.markCalls)
    }

    @Test
    fun `이전은 한 단계 앞으로 가고 0에서 안 내려간다`() {
        val vm = viewModel()
        vm.onStartTour()
        vm.onNext(stepCount = 4)
        vm.onNext(stepCount = 4)
        vm.onPrevious()
        assertEquals(1, vm.state.value.index)
        vm.onPrevious()
        vm.onPrevious()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 0), vm.state.value)
    }

    @Test
    fun `TC-9715 시작 카드 건너뛰기는 저장하고 닫히며 다시 보이지 않는다`() {
        val vm = viewModel()
        vm.onSkip()
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, remote.markCalls)
        assertFalse(viewModel().state.value.isVisible)
    }

    @Test
    fun `뒤로 가기는 시작 카드와 단계 모두 저장하고 닫힌다(AN8-15)`() {
        val onCard = viewModel()
        onCard.onBack()
        assertFalse(onCard.state.value.isVisible)
        assertEquals(1, remote.markCalls)

        settings.guide = GuideCopy.NONE
        val onStep = viewModel()
        onStep.onStartTour()
        onStep.onNext(stepCount = 4)
        // 단계의 「건너뛰기」도 같은 종료다(D-성-310).
        onStep.onBack()
        assertFalse(onStep.state.value.isVisible)
        assertEquals(2, remote.markCalls)
        assertEquals(GuideCopy.SEEN, settings.guide)
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

    @Test
    fun `AN8-15 사본 쓰기와 서버 저장이 모두 실패해도 이번 실행 동안은 다시 띄우지 않는다`() {
        settings.isGuideSaveFailing = true
        val failing = object : GuideRemote by remote {
            override suspend fun markSeen(): Outcome<Unit> = Outcome.Failure(AppError.Network)
        }
        val vm = viewModel(failing)
        vm.onSkip()
        session.loggedIn.value = false
        session.loggedIn.value = true
        assertFalse(vm.state.value.isVisible)
    }

    @Test
    fun `보이는 중에 로그인 상태가 다시 참으로 와도 진행을 유지한다`() {
        val vm = viewModel()
        vm.onStartTour()
        vm.onNext(stepCount = 4)
        session.loggedIn.value = true
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 1), vm.state.value)
    }

    @Test
    fun `다시 보기는 시작 카드 없이 단계 0에서 시작하고 seen을 안 바꾼다`() {
        settings.guide = GuideCopy.SEEN
        val vm = viewModel()
        assertFalse(vm.state.value.isVisible)
        vm.onReplay()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 0, isReplay = true), vm.state.value)
        assertEquals(GuideCopy.SEEN, settings.guide)
        assertEquals(0, remote.markCalls)
        assertEquals(0, remote.seenCalls)
    }

    @Test
    fun `다시 보기 종료는 complete를 부르지 않는다(시작하기, 건너뛰기, 뒤로 가기)`() {
        settings.guide = GuideCopy.SEEN
        val vm = viewModel()
        vm.onReplay()
        repeat(4) { vm.onNext(stepCount = 4) }
        assertFalse(vm.state.value.isVisible)
        assertFalse(vm.state.value.isReplay)

        vm.onReplay()
        vm.onNext(stepCount = 4)
        vm.onPrevious()
        vm.onBack()
        assertFalse(vm.state.value.isVisible)
        assertEquals(0, remote.markCalls)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `다시 보기는 몇 번이든 1단계부터 다시 시작한다`() {
        settings.guide = GuideCopy.SEEN
        val vm = viewModel()
        vm.onReplay()
        vm.onNext(stepCount = 4)
        vm.onBack()
        vm.onReplay()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 0, isReplay = true), vm.state.value)
    }

    @Test
    fun `첫 안내가 보이는 중에는 다시 보기 신호가 첫 안내를 바꾸지 않는다`() {
        val vm = viewModel()
        vm.onReplay()
        assertEquals(FirstRunGuideUiState(phase = GuidePhase.START_CARD), vm.state.value)
        vm.onSkip()
        assertEquals(1, remote.markCalls, "첫 안내는 끝내면 저장된다")
    }
}
