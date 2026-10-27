package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 공지 쓰기(FR-205, 와이어프레임 M1). 크루장·운영진만 이 화면에 온다(게시판 "공지" 단추). 들어오면 공지 첫 쪽에서 지금 고정된 공지 수를
 * 센다. 권한·고정 상한은 서버가 다시 판정한다(PERMISSION_DENIED, PIN_LIMIT).
 */
@HiltViewModel
class CrewNoticeWriteViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewNoticeWriteUiState())
    val state: StateFlow<CrewNoticeWriteUiState> = _state.asStateFlow()

    private var bound: Long? = null

    fun bind(crewId: Long) {
        if (bound == crewId) return
        bound = crewId
        viewModelScope.launch {
            val notices = (crews.posts(crewId, PostList.Kind.NOTICE) as? Outcome.Success)?.value ?: return@launch
            val count = notices.posts.count { it.notice?.isPinned == true }
            // 이미 3개가 고정돼 있으면 고정을 끄고 시작한다(켜 둔 채 올리면 거부된다).
            _state.update {
                it.copy(pinnedCount = count, isPinned = it.isPinned && count < CrewNoticeWriteUiState.PIN_MAX)
            }
        }
    }

    fun onBody(text: String) =
        _state.update { it.copy(body = text.takeCodePoints(CrewNoticeWriteUiState.BODY_MAX), error = null) }

    fun onPinned(isPinned: Boolean) = _state.update { it.copy(isPinned = isPinned, error = null) }

    fun onSubmit(crewId: Long) {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.writeNotice(crewId, s.body.trim(), s.isPinned)) {
                is Outcome.Success -> _state.update { it.copy(isSaving = false, isPosted = true) }
                is Outcome.Failure -> _state.update { it.copy(isSaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }
}
