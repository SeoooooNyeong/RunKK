package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PastGoals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 지난 목표 화면 상태(피그마 F-21). [nextBefore]가 null이면 더 읽을 쪽이 없다. */
data class CrewPastGoalsUiState(
    val crewId: Long = 0,
    val goals: List<PastGoals.Goal> = emptyList(),
    val nextBefore: LocalDate? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** 첫 쪽을 한 번이라도 읽었는가(빈 목록 안내를 보일지). */
    val isLoaded: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null
)

/**
 * 지난 공동 목표(피그마 F-21, contract-W7 4장): GET /crews/{id}/goals/history를 최신 주부터 읽고 끝까지 내리면 nextBefore로 다음 쪽을
 * 이어 받는다. 크루원만 볼 수 있고 권한은 서버가 검사한다.
 */
@HiltViewModel
class CrewPastGoalsViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewPastGoalsUiState())
    val state: StateFlow<CrewPastGoalsUiState> = _state.asStateFlow()

    private var bound: Long? = null
    private var job: Job? = null
    private var moreJob: Job? = null

    fun bind(crewId: Long) {
        if (bound == crewId) return
        bound = crewId
        _state.value = CrewPastGoalsUiState(crewId = crewId)
        load()
    }

    /** 첫 쪽부터 다시 읽는다. */
    fun load() {
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val r = crews.pastGoals(_state.value.crewId)) {
                is Outcome.Success -> _state.update {
                    it.copy(goals = r.value.goals, nextBefore = r.value.nextBefore, isLoading = false, isLoaded = true)
                }

                is Outcome.Failure -> _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 다음 쪽(끝이거나 읽는 중이면 하지 않는다). 실패하면 목록은 두고 안내한다. */
    fun onLoadMore() {
        val s = _state.value
        val before = s.nextBefore ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val r = crews.pastGoals(s.crewId, before)) {
                is Outcome.Success -> _state.update { st ->
                    val known = st.goals.map { it.weekStart }.toSet()
                    st.copy(
                        goals = st.goals + r.value.goals.filterNot { it.weekStart in known },
                        nextBefore = r.value.nextBefore,
                        isLoadingMore = false
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
