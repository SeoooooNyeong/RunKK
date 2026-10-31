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
 * 첫 사용 가이드(J-1~J-8, D-277, FR-231). 로그인 상태가 참이 될 때마다 FirstRunGuide.shouldShow로 띄울지 정하고
 * (로그인 상태는 ui.home이 넘긴다), "건너뛰기"·마지막 "시작하기"는 complete로 끝낸다. 측정·대결·기록 상태는 건드리지 않는다.
 */
@HiltViewModel
class FirstRunGuideViewModel @Inject constructor(
    private val guide: FirstRunGuide,
    private val session: SessionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FirstRunGuideUiState())
    val state: StateFlow<FirstRunGuideUiState> = _state.asStateFlow()

    /** 이번 실행에서 끝냈는가. 기기 사본 쓰기까지 실패해도 이번 실행 동안은 다시 띄우지 않는다(AN8-15). */
    private var isFinished = false

    init {
        viewModelScope.launch {
            session.loggedIn.collectLatest { loggedIn ->
                // 보이는 중에 로그인 상태가 다시 참으로 오면(세션 갱신) 진행을 처음으로 돌리지 않는다.
                if (loggedIn && _state.value.isVisible) return@collectLatest
                if (isFinished) return@collectLatest
                val isShown = guide.shouldShow(loggedIn)
                _state.value = FirstRunGuideUiState(isVisible = isShown, index = 0)
            }
        }
    }

    /** "다음"(마지막 단계에서는 "시작하기"). [stepCount]는 화면이 거른 단계 수다. */
    fun onNext(stepCount: Int) {
        val now = _state.value
        if (!now.isVisible) return
        if (now.isLastOf(stepCount)) finish() else _state.update { it.copy(index = it.index + 1) }
    }

    /** "건너뛰기": 끝낸 것으로 저장한다. */
    fun onSkip() {
        if (_state.value.isVisible) finish()
    }

    private fun finish() {
        isFinished = true
        _state.update { it.copy(isVisible = false) }
        // 서버 저장 실패는 FirstRunGuide가 기기 사본으로 남겨 다음 시작 때 다시 보낸다. 화면이 바로 정리돼도
        // 사본 쓰기가 끊기지 않게 취소하지 않는다(끊기면 가이드가 다시 뜬다).
        viewModelScope.launch { withContext(NonCancellable) { guide.complete() } }
    }
}
