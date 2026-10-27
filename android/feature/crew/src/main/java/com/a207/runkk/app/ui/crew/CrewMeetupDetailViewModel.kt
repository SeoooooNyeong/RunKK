package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 대면 모임 상세(FR-145·FR-207, FR-213, 와이어프레임 M7). 대면 모임 목록에서 그 글을 찾아 일시·장소·정원·응답 수를 보이고, 참석·불참·미정으로
 * 답한다(PUT /posts/{id}/attendance). 정원이 차면 서버가 SCHEDULE_FULL로 거부한다. 사람별 응답 명단은 서버가 주지 않는다.
 */
@HiltViewModel
class CrewMeetupDetailViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewMeetupDetailUiState())
    val state: StateFlow<CrewMeetupDetailUiState> = _state.asStateFlow()

    private var bound: Long? = null

    fun bind(crewId: Long, postId: Long) {
        if (bound == postId) return
        bound = postId
        _state.value = CrewMeetupDetailUiState(crewId = crewId, postId = postId, now = clock.instant())
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = _state.value
        val post = crews.findPost(s.crewId, PostList.Kind.MEETUP, s.postId)
        _state.update {
            it.copy(
                isLoading = false,
                post = post ?: it.post,
                error = if (post == null && it.post == null) uiText(R.string.crew_error_post_not_found) else null
            )
        }
    }

    fun onRetry() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { load() }
    }

    fun onAnswer(answer: AttendanceAnswer) {
        val s = _state.value
        if (s.isAnswering || s.post?.meetup?.myAnswer == answer) return
        _state.update { it.copy(isAnswering = true) }
        viewModelScope.launch {
            val r = crews.answerAttendance(s.postId, answer)
            if (r is Outcome.Failure) _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            load()
            _state.update { it.copy(isAnswering = false) }
        }
    }

    fun onDeleteAsk() {
        if (_state.value.post?.canDelete == true) _state.update { it.copy(isConfirmingDelete = true) }
    }

    fun onDeleteDismiss() = _state.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        _state.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch {
            when (val r = crews.deletePost(_state.value.postId)) {
                is Outcome.Success -> _state.update { it.copy(isDeleted = true) }
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
