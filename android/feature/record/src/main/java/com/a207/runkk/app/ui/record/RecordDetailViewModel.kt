package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodySession
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 기록 상세(G-9, FR-059)와 기록 삭제(FR-225, D-280). 삭제는 RecordDetail.isDeletable일 때만 확인을 묻고 RecordRepository.delete를
 * 한 번 부른다. 204와 RECORD_NOT_FOUND(이미 없음)는 [RecordDetailEvent.DELETED]로 목록에 돌려보내고, RECORD_VALID_NOT_DELETABLE은
 * "삭제할 수 없는 기록이에요"를 보이고 버튼을 숨긴다(서버 판정 우선). 그 밖(403 PERMISSION_DENIED 등)은 일반 오류 문구다.
 * 유효 러닝이면 런바디 분석 상태를 한 번 읽어 진입 카드([RecordRunBody])를 정한다(GET /runbody/sessions/{recordId}).
 */
@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val records: RecordRepository,
    private val runBody: RunBodyRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RecordDetailUiState())
    val state: StateFlow<RecordDetailUiState> = _state.asStateFlow()
    private val _events = Channel<RecordDetailEvent>(Channel.BUFFERED)
    val events: Flow<RecordDetailEvent> = _events.receiveAsFlow()

    fun load(recordId: String, isForced: Boolean = false) {
        if (!isForced && _state.value.recordId == recordId && _state.value.detail != null) return
        _state.value = RecordDetailUiState(recordId = recordId)
        viewModelScope.launch {
            when (val result = records.run(recordId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isLoading = false, detail = result.value) }
                    if (result.value.summary.isValid) readRunBody(recordId)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RecordErrorText.of(result.error))
                }
            }
        }
    }

    /** 런바디 진입 카드 상태를 한 번 읽는다(유효 러닝만). 기능이 꺼졌으면 카드를 숨긴다. */
    private suspend fun readRunBody(recordId: String) {
        val card = when (val result = runBody.session(recordId)) {
            is Outcome.Success -> cardOf(result.value)
            is Outcome.Failure -> cardOf(result.error)
        }
        _state.update { if (it.recordId == recordId) it.copy(runBody = card) else it }
    }

    private fun cardOf(session: RunBodySession): RecordRunBody {
        val result = session.result
        return when {
            session.status.isWaiting -> RecordRunBody.Analyzing

            session.status == RunBodyStatus.DONE && result != null -> RecordRunBody.Ready(
                intensity = result.axes[RunBodyAxis.INTENSITY]?.score,
                endurance = result.axes[RunBodyAxis.ENDURANCE]?.score,
                stability = result.axes[RunBodyAxis.STABILITY]?.score
            )

            else -> RecordRunBody.Unavailable
        }
    }

    private fun cardOf(error: AppError): RecordRunBody =
        if ((error as? AppError.Rejected)?.code == RunBodyRepository.FEATURE_CLOSED) {
            RecordRunBody.Hidden
        } else {
            RecordRunBody.Unavailable
        }

    fun onDeleteTapped() {
        if (_state.value.detail?.isDeletable != true) return
        _state.update { it.copy(isConfirmingDelete = true) }
    }

    fun onDeleteDismissed() = _state.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirmed() {
        val s = _state.value
        val recordId = s.recordId ?: return
        _state.update { it.copy(isConfirmingDelete = false) }
        if (s.isDeleting || s.detail?.isDeletable != true) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = records.delete(recordId)) {
                is Outcome.Success -> _events.send(RecordDetailEvent.DELETED)
                is Outcome.Failure -> onDeleteFailed(result.error)
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private suspend fun onDeleteFailed(error: AppError) {
        when ((error as? AppError.Rejected)?.code) {
            RECORD_NOT_FOUND -> _events.send(RecordDetailEvent.DELETED)

            RECORD_VALID_NOT_DELETABLE -> _state.update {
                it.copy(
                    isDeleting = false,
                    detail = it.detail?.copy(isDeletable = false),
                    message = UiText.Res(R.string.record_delete_not_allowed)
                )
            }

            else -> _state.update { it.copy(isDeleting = false, message = RunkkErrorText.of(error)) }
        }
    }

    private companion object {
        const val RECORD_NOT_FOUND = "RECORD_NOT_FOUND"
        const val RECORD_VALID_NOT_DELETABLE = "RECORD_VALID_NOT_DELETABLE"
    }
}
