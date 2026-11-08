package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunForm
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
 * 크루런 모집하기(FR-132, 피그마 F-12): 제목·내용·거리 1·3·5·10km·모집 일시. 대면 모임은 앱에서 만들지 않는다.
 * 지난 일시는 앱이 먼저 막고 서버도 거부한다. 날짜 칩과 고른 시각은 기기 시간대 [zone]으로 읽어 모집 일시로 바꾼다
 * (앱 Clock은 UTC라 clock.zone으로 바꾸면 9시간 어긋난다, MR !91 e2e 2 후속).
 */
@HiltViewModel
class CrewRunWriteViewModel internal constructor(
    private val crews: CrewRepository,
    private val clock: Clock,
    private val zone: ZoneId
) : ViewModel() {
    @Inject
    constructor(crews: CrewRepository, clock: Clock) : this(crews, clock, ZoneId.systemDefault())

    private val _state = MutableStateFlow(CrewRunWriteUiState(today = clock.instant().atZone(zone).toLocalDate()))
    val state: StateFlow<CrewRunWriteUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunWriteEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunWriteEvent> = _events.receiveAsFlow()

    fun onTitle(text: String) = _state.update { it.copy(title = text.take(TITLE_MAX), error = null) }

    fun onBody(text: String) = _state.update { it.copy(body = text.takeCodePoints(BODY_MAX)) }

    fun onDistance(distance: Distance) {
        if (distance in CREW_RUN_DISTANCES) _state.update { it.copy(distance = distance) }
    }

    fun onDay(offset: Int) = _state.update { it.copy(dayOffset = offset.coerceIn(0, CrewRunWriteUiState.DAYS - 1)) }

    fun onTime(time: LocalTime) = _state.update { it.copy(time = time) }

    fun onSubmit(crewId: Long) {
        val s = _state.value
        if (!s.isValid || s.isSaving) return
        val meetAt = s.today.plusDays(s.dayOffset.toLong()).atTime(s.time).atZone(zone).toInstant()
        if (!meetAt.isAfter(clock.instant())) {
            _state.update { it.copy(error = uiText(R.string.crew_run_write_past_time)) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val r = crews.writeCrewRun(
                crewId,
                CrewRunForm(s.title.trim(), s.body.trim().ifBlank { null }, s.distance, meetAt)
            )
            when (r) {
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
