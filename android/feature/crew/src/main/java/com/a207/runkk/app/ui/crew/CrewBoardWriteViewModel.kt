package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 게시판 글쓰기(FR-211). 사진은 0~5장(6장째는 붙이지 않고 알린다, TC-7439). 고를 때 10MB 넘거나 JPEG·PNG가 아닌 사진은 화면이 걸러
 * [onPhotosPicked]의 rejectedCount로 알린다(TC-7440·TC-7441). 올릴 때 줄이다 거부되면(TOO_LARGE·UNSUPPORTED_IMAGE, D-142)
 * 그 이유를 보이고 화면에 머문다.
 */
@HiltViewModel
class CrewBoardWriteViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewBoardWriteUiState())
    val state: StateFlow<CrewBoardWriteUiState> = _state.asStateFlow()

    fun onBody(text: String) = _state.update {
        it.copy(body = text.takeCodePoints(CrewBoardWriteUiState.BODY_MAX), error = null)
    }

    /** 고른 사진을 더한다. 이미 고른 사진은 다시 더하지 않고, 5장을 넘는 사진은 버리고 알린다. */
    fun onPhotosPicked(uris: List<String>, rejectedCount: Int) = _state.update { s ->
        val fresh = uris.filterNot { it in s.photos }
        val room = CrewBoardWriteUiState.PHOTO_MAX - s.photos.size
        val message = when {
            fresh.size > room -> uiText(R.string.crew_board_photo_limit)
            rejectedCount > 0 -> uiText(R.string.crew_board_photo_rejected)
            else -> s.message
        }
        s.copy(photos = s.photos + fresh.take(room.coerceAtLeast(0)), message = message, error = null)
    }

    fun onRemovePhoto(uri: String) = _state.update { it.copy(photos = it.photos - uri) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onSubmit(crewId: Long) {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.writeBoard(crewId, s.body.trim(), s.photos)) {
                is Outcome.Success -> _state.update { it.copy(isSaving = false, isPosted = true) }
                is Outcome.Failure -> _state.update { it.copy(isSaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }
}
