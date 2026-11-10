package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루런 일정 상세(GET /crew-runs/{id}, 계약 contract-W6.md): 참석하기(PUT GOING)·참석 취소하기(DELETE, 응답을 거둔다), 작성자 메뉴
 * 수정·삭제(F-13b·F-13c). 응답·수정에서 돌아오면 다시 읽어 참석자 목록을 맞춘다.
 */
@HiltViewModel
class CrewRunDetailViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewRunDetailUiState(now = clock.instant()))
    val state: StateFlow<CrewRunDetailUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunDetailEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunDetailEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    fun bind(postId: Long) {
        if (_state.value.postId == postId) return
        _state.value = CrewRunDetailUiState(postId = postId, now = clock.instant())
        refresh()
    }

    /** 다시 읽는다(처음, 다시 시도, 수정 화면에서 돌아옴). */
    fun refresh() {
        val postId = _state.value.postId
        if (postId == 0L) return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = it.detail == null, loadError = null, now = clock.instant()) }
        loadJob = viewModelScope.launch {
            when (val r = crews.crewRun(postId)) {
                is Outcome.Success -> _state.update { it.copy(detail = r.value, isLoading = false) }

                is Outcome.Failure -> _state.update {
                    if (it.detail == null) {
                        it.copy(isLoading = false, loadError = CrewErrorText.of(r.error))
                    } else {
                        it.copy(isLoading = false, message = CrewErrorText.of(r.error))
                    }
                }
            }
        }
    }

    /** 참석하기(PUT /posts/{id}/attendance GOING). */
    fun onAttend() = answer { crews.answerAttendance(it, AttendanceAnswer.GOING) }

    /** 참석 취소하기(DELETE /posts/{id}/attendance). */
    fun onCancelAttend() = answer { crews.withdrawAttendance(it) }

    private fun answer(call: suspend (Long) -> Outcome<Unit>) {
        // 화면을 오래 열어 둔 사이 시작 시각이 지났으면 버튼을 거두고 보내지 않는다(자가 리뷰 M6).
        _state.update { it.copy(now = clock.instant()) }
        val s = _state.value
        if (s.isAnswering || s.detail == null || !s.canAnswer) return
        _state.update { it.copy(isAnswering = true) }
        viewModelScope.launch {
            val r = call(s.postId)
            _state.update { it.copy(isAnswering = false) }
            when (r) {
                is Outcome.Success -> refresh()
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMenu(isOpen: Boolean) = _state.update { it.copy(isMenuOpen = isOpen) }

    fun onDeleteAsk() = _state.update { it.copy(isMenuOpen = false, isDeleteAsked = true) }

    fun onDeleteDismiss() = _state.update { it.copy(isDeleteAsked = false) }

    /** 삭제(DELETE /posts/{id}). 반복의 최신 회차면 서버가 반복도 끝낸다. */
    fun onDeleteConfirm() {
        val s = _state.value
        if (s.isDeleting) return
        _state.update { it.copy(isDeleteAsked = false, isDeleting = true) }
        viewModelScope.launch {
            when (val r = crews.deletePost(s.postId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isDeleting = false) }
                    _events.trySend(CrewRunDetailEvent.Deleted)
                }

                is Outcome.Failure -> _state.update { it.copy(isDeleting = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}

/** 신고 대화상자(CrewReportViewModel.onAsk)에 넘기는 글 한 줄. */
internal fun CrewRunDetail.asPostItem(): PostList.Item = PostList.Item(
    postId = postId,
    kind = PostList.Kind.CREW_RUN,
    authorName = authorName,
    summary = title,
    writtenAt = writtenAt,
    isMine = isMine,
    canDelete = canDelete
)
