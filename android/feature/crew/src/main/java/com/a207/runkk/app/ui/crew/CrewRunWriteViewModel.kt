package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.MeetupForm
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
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
 * 일정 쓰기(FR-132, FR-207): 크루런(제목·내용·거리 1·3·5·10km·모집 일시, 장소 칸 없음)과 대면 모임(내용·장소·정원·일시).
 * 지난 일시는 앱이 먼저 막고 서버도 거부한다.
 */
@HiltViewModel
class CrewRunWriteViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewRunWriteUiState(today = LocalDate.now(clock)))
    val state: StateFlow<CrewRunWriteUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunWriteEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunWriteEvent> = _events.receiveAsFlow()

    /** 일정 종류(크루런·대면 모임, FR-132). */
    fun onKind(kind: PostList.Kind) {
        if (kind == PostList.Kind.CREW_RUN ||
            kind == PostList.Kind.MEETUP
        ) {
            _state.update { it.copy(kind = kind, error = null) }
        }
    }

    fun onPlace(text: String) = _state.update {
        it.copy(placeName = text.takeCodePoints(CrewRunWriteUiState.PLACE_MAX), error = null)
    }

    /** 정원 2~10명. 범위 밖이거나 null이면 크루 정원(비움). */
    fun onCapacity(capacity: Int?) = _state.update {
        it.copy(
            capacity = capacity?.takeIf { c ->
                c in
                    CrewRunWriteUiState.CAPACITY_MIN..CrewRunWriteUiState.CAPACITY_MAX
            }
        )
    }

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
        val meetAt = s.today.plusDays(s.dayOffset.toLong()).atTime(s.time).atZone(clock.zone).toInstant()
        if (!meetAt.isAfter(clock.instant())) {
            _state.update { it.copy(error = uiText(R.string.crew_run_write_past_time)) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val r = if (s.kind == PostList.Kind.MEETUP) {
                crews.writeMeetup(crewId, MeetupForm(s.body.trim(), meetAt, s.placeName.trim(), s.capacity))
            } else {
                crews.writeCrewRun(
                    crewId,
                    CrewRunForm(
                        s.title.trim(),
                        s.body.trim().ifBlank {
                            null
                        },
                        s.distance,
                        meetAt
                    )
                )
            }
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
