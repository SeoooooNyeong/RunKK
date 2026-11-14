package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.settings.FirstRunGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 첫 사용 가이드([임시:FR-성-35], [임시:FR-성-36], [임시:D-성-297], D-277). 로그인 상태가 참이 될 때마다 FirstRunGuide.shouldShow로
 * 시작 카드를 띄울지 정한다(로그인 상태는 ui.home이 넘긴다). 첫 안내는 시작 카드의 「건너뛰기」, 단계의 「건너뛰기」·마지막 「시작하기」,
 * 뒤로 가기 모두 complete로 끝낸다(본 것으로 저장). 다시 보기([onReplay])는 1단계부터 보이고 끝내도 저장하지 않는다.
 * 측정·대결·기록 상태는 건드리지 않는다.
 */
@HiltViewModel
class FirstRunGuideViewModel @Inject constructor(
    private val guide: FirstRunGuide,
    private val session: SessionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FirstRunGuideUiState())
    val state: StateFlow<FirstRunGuideUiState> = _state.asStateFlow()

    /** 이번 실행에서 첫 안내를 끝냈는가. 기기 사본 쓰기까지 실패해도 이번 실행 동안은 다시 띄우지 않는다(AN8-15). */
    private var isFinished = false

    init {
        viewModelScope.launch {
            session.loggedIn.collectLatest { loggedIn ->
                // 보이는 중에 로그인 상태가 다시 참으로 오면(세션 갱신) 진행을 처음으로 돌리지 않는다.
                if (loggedIn && _state.value.isVisible) return@collectLatest
                if (isFinished) return@collectLatest
                val isShown = guide.shouldShow(loggedIn)
                // 판정을 기다리는 사이 다시 보기가 열렸거나 첫 안내를 끝냈으면 덮어쓰지 않는다.
                _state.update { now ->
                    when {
                        isFinished -> now
                        loggedIn && now.isVisible -> now
                        else -> FirstRunGuideUiState(phase = if (isShown) GuidePhase.START_CARD else GuidePhase.HIDDEN)
                    }
                }
            }
        }
    }

    /** 시작 카드의 "둘러보기": 1단계 말풍선으로 간다. */
    fun onStartTour() {
        _state.update { if (it.phase == GuidePhase.START_CARD) it.copy(phase = GuidePhase.STEPS, index = 0) else it }
    }

    /** "다음"(마지막 단계에서는 "시작하기"). [stepCount]는 화면이 거른 단계 수다. */
    fun onNext(stepCount: Int) {
        val now = _state.value
        if (now.phase != GuidePhase.STEPS) return
        if (now.isLastOf(stepCount)) close() else _state.update { it.copy(index = it.index + 1) }
    }

    /** "이전": 한 단계 앞으로. 1단계에서는 아무것도 하지 않는다(단추가 없다). */
    fun onPrevious() {
        _state.update { if (it.phase == GuidePhase.STEPS && it.index > 0) it.copy(index = it.index - 1) else it }
    }

    /** 시작 카드의 "건너뛰기": 끝낸 것으로 저장한다. */
    fun onSkip() {
        if (_state.value.phase == GuidePhase.START_CARD) close()
    }

    /** 뒤로 가기와 단계의 "건너뛰기"([임시:D-성-310]): 시작 카드와 단계 모두 가이드를 닫는다(첫 안내면 저장, AN8-15). */
    fun onBack() {
        if (_state.value.isVisible) close()
    }

    /**
     * 설정의 「가이드 다시 보기」([임시:FR-성-36]): 시작 카드 없이 1단계부터 보인다. 본 표시(기기 사본·서버)는 건드리지 않는다.
     * 첫 안내가 보이는 중이면 그대로 둔다(첫 안내를 끝내야 본 것으로 저장된다).
     */
    fun onReplay() {
        val now = _state.value
        if (now.isVisible && !now.isReplay) return
        _state.value = FirstRunGuideUiState(phase = GuidePhase.STEPS, index = 0, isReplay = true)
    }

    /** 가이드를 닫는다. 첫 안내면 본 것으로 저장하고, 다시 보기면 닫기만 한다. */
    private fun close() {
        val isReplay = _state.value.isReplay
        _state.value = FirstRunGuideUiState()
        if (isReplay) return
        isFinished = true
        // 서버 저장 실패는 FirstRunGuide가 기기 사본으로 남겨 다음 시작 때 다시 보낸다. 화면이 바로 정리돼도
        // 사본 쓰기가 끊기지 않게 취소하지 않는다(끊기면 가이드가 다시 뜬다).
        viewModelScope.launch { withContext(NonCancellable) { guide.complete() } }
    }
}
