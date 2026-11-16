package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
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
 * 초대 링크로 연 크루 가입 확인([임시:FR-성-38], [임시:D-성-244]). 코드로 크루를 미리 보고(POST /crews/invite-preview, 가입시키지 않는다)
 * 결과를 넷으로 가른다: 정상이면 미리보기와 「가입하기」, 코드가 만료됐거나 다시 발급돼 맞지 않으면(INVALID_CODE) 만료 안내, 내 크루
 * (GET /crews/me)가 그 크루면 그 크루 홈, 시도 횟수 초과(TOO_MANY_ATTEMPTS)면 기존 문구다. 가입은 초대 코드 가입(FR-122)을 그대로 쓴다.
 */
@HiltViewModel
class CrewInviteJoinViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow<CrewInviteJoinUiState>(CrewInviteJoinUiState.Loading)
    val state: StateFlow<CrewInviteJoinUiState> = _state.asStateFlow()

    private val _events = Channel<CrewInviteJoinEvent>(Channel.BUFFERED)
    val events: Flow<CrewInviteJoinEvent> = _events.receiveAsFlow()

    private var code: String? = null

    /** 크루 로고 바이트(미리보기 카드의 크루 이미지가 읽는다). */
    suspend fun logo(url: String): Outcome<ByteArray> = crews.logo(url)
    private var loadJob: Job? = null

    /** 그 코드의 크루를 읽는다. 같은 코드로 다시 부르면(화면 재구성) 읽지 않는다. */
    fun load(code: String) {
        if (this.code == code) return
        this.code = code
        read(code)
    }

    /** 읽기 실패 뒤 다시 시도. */
    fun onRetry() {
        val code = code ?: return
        if (_state.value is CrewInviteJoinUiState.Failed) read(code)
    }

    private fun read(code: String) {
        loadJob?.cancel()
        _state.value = CrewInviteJoinUiState.Loading
        loadJob = viewModelScope.launch {
            when (val preview = crews.previewByInviteCode(code)) {
                is Outcome.Success -> {
                    // 미리보기 응답에는 "이미 구성원"이 없어 내 크루와 견준다. 내 크루를 못 읽으면 가입 확인을 보이고 서버가 판정한다.
                    val mine = (crews.myCrew() as? Outcome.Success)?.value?.crew?.crewId
                    if (mine == preview.value.crewId) {
                        _state.value = CrewInviteJoinUiState.AlreadyMember(mine)
                        _events.trySend(CrewInviteJoinEvent.OpenCrewHome(mine))
                    } else {
                        _state.value = CrewInviteJoinUiState.Found(preview.value)
                    }
                }

                is Outcome.Failure ->
                    _state.value = codeFailure(preview.error)
                        ?: CrewInviteJoinUiState.Failed(CrewErrorText.of(preview.error))
            }
        }
    }

    /** 「가입하기」(FR-122): 가입되면 그 크루 홈을 연다. 그 사이 코드가 만료됐으면 만료 안내, 그 밖의 거부는 사유를 보인다. */
    fun onJoin() {
        val code = code ?: return
        val found = _state.value as? CrewInviteJoinUiState.Found ?: return
        if (found.isJoining) return
        _state.value = found.copy(isJoining = true, message = null)
        viewModelScope.launch {
            when (val result = crews.applyWithInviteCode(code)) {
                is Outcome.Success -> _events.trySend(CrewInviteJoinEvent.OpenCrewHome(result.value.crewId))

                is Outcome.Failure -> _state.update { current ->
                    codeFailure(result.error)
                        ?: (current as? CrewInviteJoinUiState.Found)
                            ?.copy(isJoining = false, message = CrewErrorText.of(result.error))
                        ?: current
                }
            }
        }
    }

    /** 만료 안내의 「크루 찾기」. */
    fun onFindCrews() {
        _events.trySend(CrewInviteJoinEvent.OpenSearch)
    }

    /** 코드 때문에 생긴 실패의 상태. 그 밖의 실패는 null. */
    private fun codeFailure(error: AppError): CrewInviteJoinUiState? = when (error) {
        AppError.Rejected(INVALID_CODE) -> CrewInviteJoinUiState.Expired
        AppError.Rejected(TOO_MANY_ATTEMPTS) -> CrewInviteJoinUiState.Limited
        else -> null
    }

    private companion object {
        const val INVALID_CODE = "INVALID_CODE"
        const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
    }
}
