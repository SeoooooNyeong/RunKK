package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
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
 * 투표 응답과 결과(FR-206·FR-207, FR-213, 와이어프레임 M3). 결과(GET /posts/{id}/poll)를 읽고, 작성자·시각·삭제 가능 여부는 투표 목록에서
 * 그 글을 찾는다(몇 쪽까지). 마감 전이면 선택지 하나로 응답하거나 바꾸고, 마감 뒤 응답은 서버가 POLL_CLOSED로 거부한다.
 */
@HiltViewModel
class CrewPollDetailViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewPollDetailUiState())
    val state: StateFlow<CrewPollDetailUiState> = _state.asStateFlow()

    private var bound: Long? = null

    fun bind(crewId: Long, postId: Long) {
        if (bound == postId) return
        bound = postId
        _state.value = CrewPollDetailUiState(crewId = crewId, postId = postId, now = clock.instant())
        viewModelScope.launch { _state.update { it.copy(post = crews.findPost(crewId, PostList.Kind.POLL, postId)) } }
        viewModelScope.launch { loadResult() }
    }

    private suspend fun loadResult() {
        when (val r = crews.pollResult(_state.value.postId)) {
            is Outcome.Success -> _state.update { it.copy(isLoading = false, result = r.value, error = null) }
            is Outcome.Failure -> _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
        }
    }

    fun onRetry() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { loadResult() }
    }

    /** 선택지 하나로 응답한다(다시 고르면 바꾼다, FR-207). 끝나면 결과를 다시 읽는다. */
    fun onRespond(optionNo: Int) {
        val s = _state.value
        val result = s.result ?: return
        if (s.isResponding || result.isClosed || result.myOptionNo == optionNo) return
        _state.update { it.copy(isResponding = true) }
        viewModelScope.launch {
            val r = crews.respondPoll(s.postId, optionNo)
            if (r is Outcome.Failure) _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            loadResult()
            _state.update { it.copy(isResponding = false) }
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
                is Outcome.Success -> _state.update {
                    it.copy(isDeleted = true, message = uiText(R.string.crew_home_post_deleted))
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
