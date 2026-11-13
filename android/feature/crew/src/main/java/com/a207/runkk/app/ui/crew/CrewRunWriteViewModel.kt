package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunEdit
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
 * 크루런 모집하기·수정(FR-132, 피그마 F-12·F-13d·F-13e, 계약 contract-W6.md): 제목·내용 따로, 날짜(오늘~14일 뒤)·시간(5분 단위)·거리
 * (제한 없음·3·5·10km·하프)·반복(매주). 수정이면 GET /crew-runs/{id}로 칸을 채우고 날짜·시간은 바꾸지 않는다. 지난 시각은 앱이 먼저 막고
 * 서버도 거부한다. 날짜와 시간은 기기 시간대 [zone]으로 읽어 모집 일시로 바꾼다(앱 Clock은 UTC라 clock.zone을 쓰지 않는다).
 */
@HiltViewModel
class CrewRunWriteViewModel internal constructor(
    private val crews: CrewRepository,
    private val clock: Clock,
    private val zone: ZoneId
) : ViewModel() {
    @Inject
    constructor(crews: CrewRepository, clock: Clock) : this(crews, clock, ZoneId.systemDefault())

    private val _state = MutableStateFlow(CrewRunWriteUiState(today = today()))
    val state: StateFlow<CrewRunWriteUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunWriteEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunWriteEvent> = _events.receiveAsFlow()

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    /** 화면 모드를 한 번 정한다. 수정이면 지금 글을 읽어 칸을 채운다. */
    fun bind(mode: CrewRunWriteMode) {
        if (_state.value.mode == mode) return
        _state.value = CrewRunWriteUiState(mode = mode, today = today())
        if (mode is CrewRunWriteMode.Edit) load(mode.postId)
    }

    fun onRetry() {
        (_state.value.mode as? CrewRunWriteMode.Edit)?.let { load(it.postId) }
    }

    private fun load(postId: Long) {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = crews.crewRun(postId)) {
                is Outcome.Success -> _state.update { it.filledWith(r.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, loadError = CrewErrorText.of(r.error))
                }
            }
        }
    }

    private fun CrewRunWriteUiState.filledWith(run: CrewRunDetail): CrewRunWriteUiState {
        val at = run.scheduledAt.atZone(zone)
        val choice = CrewRunDistanceChoice.of(run.distance)
        return copy(
            title = run.title,
            body = run.body,
            date = at.toLocalDate(),
            time = at.toLocalTime(),
            distance = choice,
            legacyDistance = if (choice == null) run.distance else null,
            repeat = run.repeat,
            isLoading = false
        )
    }

    fun onTitle(text: String) = _state.update { it.copy(title = text.takeCodePoints(TITLE_MAX), error = null) }

    fun onBody(text: String) = _state.update { it.copy(body = text.takeCodePoints(BODY_MAX), error = null) }

    /** 시트를 연다. 수정 화면의 날짜·시간은 잠겨 열지 않는다. */
    fun onSheet(sheet: CrewRunWriteSheet) {
        val s = _state.value
        val isLocked = s.isEdit && (sheet == CrewRunWriteSheet.DATE || sheet == CrewRunWriteSheet.TIME)
        if (isLocked || s.isLoading) return
        _state.update { it.copy(sheet = sheet) }
    }

    fun onSheetDismiss() = _state.update { it.copy(sheet = null) }

    /** 날짜(오늘~14일 뒤만). 범위 밖이면 무시한다. */
    fun onDate(date: LocalDate) {
        val s = _state.value
        if (s.isEdit || date.isBefore(s.today) || date.isAfter(s.lastDate)) return
        _state.update { it.copy(date = date, sheet = null, error = null) }
    }

    /** 시간(5분 단위만). */
    fun onTime(time: LocalTime) {
        if (_state.value.isEdit || time.minute % CrewRunWriteUiState.MINUTE_STEP != 0) return
        _state.update { it.copy(time = time.withSecond(0).withNano(0), sheet = null, error = null) }
    }

    fun onDistance(choice: CrewRunDistanceChoice) =
        _state.update { it.copy(distance = choice, legacyDistance = null, sheet = null, error = null) }

    fun onRepeat(repeat: CrewRunRepeat) = _state.update { it.copy(repeat = repeat, sheet = null) }

    fun onSubmit() {
        val s = _state.value
        if (!s.isValid || s.isSaving) return
        when (val mode = s.mode) {
            is CrewRunWriteMode.Create -> create(mode.crewId, s)
            is CrewRunWriteMode.Edit -> edit(mode.postId, s)
            null -> Unit
        }
    }

    private fun create(crewId: Long, s: CrewRunWriteUiState) {
        val date = s.date ?: return
        val time = s.time ?: return
        val meetAt = date.atTime(time).atZone(zone).toInstant()
        if (!meetAt.isAfter(clock.instant())) {
            _state.update { it.copy(error = uiText(R.string.crew_run_write_past_time)) }
            return
        }
        val form = CrewRunForm(s.title.trim(), s.body.trim().ifBlank { null }, s.distanceToSend, meetAt, s.repeat)
        save { crews.writeCrewRun(crewId, form) }
    }

    private fun edit(postId: Long, s: CrewRunWriteUiState) {
        val edit = CrewRunEdit(s.title.trim(), s.body.trim().ifBlank { null }, s.distanceToSend, s.repeat)
        save { crews.editCrewRun(postId, edit) }
    }

    private fun save(call: suspend () -> Outcome<*>) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is Outcome.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.trySend(CrewRunWriteEvent.Posted)
                }

                is Outcome.Failure -> _state.update { it.copy(isSaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    private companion object {
        const val TITLE_MAX = 30
        const val BODY_MAX = 200
    }
}
