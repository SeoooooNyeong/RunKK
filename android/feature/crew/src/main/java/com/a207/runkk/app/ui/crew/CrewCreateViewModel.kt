package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * 크루 만들기(FR-110, 피그마 F-04): 만든 러너가 크루장이 된다. 로고를 고르면 바로 올리고(POST /crew-logos, JPEG 1MB 이하) 받은 주소를
 * 입력에 싣는다. 만들면 크루 탭(내 크루 홈)으로.
 */
@HiltViewModel
class CrewCreateViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewCreateUiState())
    val state: StateFlow<CrewCreateUiState> = _state.asStateFlow()

    private val _events = Channel<CrewEvent>(Channel.BUFFERED)
    val events: Flow<CrewEvent> = _events.receiveAsFlow()

    private var logoJob: Job? = null

    /** 크루 로고 바이트(크루 이미지가 읽는다). */
    suspend fun logo(url: String): Outcome<ByteArray> = crews.logo(url)

    /** 입력 칸 변경. 로고 주소는 업로드가 정하므로 화면이 넘긴 옛 값으로 되돌리지 않는다. */
    fun onForm(form: CrewFormState) =
        _state.update { it.copy(form = form.copy(logoUrl = it.form.logoUrl), error = null) }

    /** 고른 로고(JPEG로 줄인 바이트)를 미리 보이고 올린다. 실패하면 로고를 비우고 안내한다. */
    fun onLogoPicked(jpeg: ByteArray) {
        logoJob?.cancel()
        _state.update {
            it.copy(logo = jpeg, isUploadingLogo = true, error = null, form = it.form.copy(logoUrl = null))
        }
        logoJob = viewModelScope.launch {
            when (val r = crews.uploadLogo(jpeg)) {
                is Outcome.Success -> _state.update {
                    it.copy(isUploadingLogo = false, form = it.form.copy(logoUrl = r.value))
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isUploadingLogo = false, logo = null, error = CrewErrorText.of(r.error))
                }
            }
        }
    }

    /** 고른 사진을 읽지 못했다(디코딩 실패). */
    fun onLogoUnreadable() = _state.update { it.copy(error = uiText(R.string.crew_create_logo_failed)) }

    fun onSubmit() {
        val s = _state.value
        if (!s.canSubmit || s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = crews.create(s.form.toForm())) {
                is Outcome.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.trySend(CrewEvent.Created)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, error = CrewErrorText.of(result.error))
                }
            }
        }
    }
}
