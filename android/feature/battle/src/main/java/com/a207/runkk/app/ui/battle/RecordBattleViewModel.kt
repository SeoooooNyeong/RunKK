package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
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
 * 나와의 싸움·유명인 대결 공통 흐름(FR-096·FR-097·FR-100): 후보 조회 → 고르기 → 시작 요청 →
 * BattleRunCoordinator.onRecordBattleStart(3초 카운트다운 뒤 측정, design-review.md M34) → 대결 진행 화면.
 */
abstract class RecordBattleViewModel(
    protected val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val kind: OpponentKind
) : ViewModel() {
    protected val mutableState = MutableStateFlow(RecordBattleUiState())
    val state: StateFlow<RecordBattleUiState> = mutableState.asStateFlow()

    private val _events = Channel<RecordBattleEvent>(Channel.BUFFERED)
    val events: Flow<RecordBattleEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    protected abstract suspend fun fetch(): Outcome<RecordBattleCandidates>

    protected abstract suspend fun begin(candidate: CandidateUi): Outcome<RecordBattleStart>

    fun load() {
        loadJob?.cancel()
        mutableState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = fetch()) {
                is Outcome.Success -> mutableState.update { s ->
                    val list = result.value.candidates.map(CandidateUi::of)
                    s.copy(isLoading = false, candidates = list, selected = s.selected?.takeIf { it in list })
                }

                is Outcome.Failure -> mutableState.update {
                    it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }

    fun onSelect(candidate: CandidateUi) {
        if (!candidate.isSelectable) return
        mutableState.update { it.copy(selected = candidate) }
    }

    fun onMessageShown() = mutableState.update { it.copy(message = null) }

    /** 화면에 다시 돌아왔을 때(대결을 마치고 탭으로 복귀). */
    fun onScreenResumed() = mutableState.update { it.copy(isStarting = false) }

    /** 고른 후보로 대결을 시작한다. 이미 시작 중이면 무시한다. */
    fun onStart() {
        val s = mutableState.value
        val candidate = s.selected ?: return
        if (s.isStarting) return
        mutableState.update { it.copy(isStarting = true, isConfirming = false) }
        viewModelScope.launch {
            when (val started = begin(candidate)) {
                is Outcome.Success -> {
                    val start = started.value
                    coordinator.onRecordBattleStart(start)
                    // 진행 화면으로 넘어갈 때까지 다시 누르지 못하게 isStarting을 둔다. 화면에 돌아오면 onScreenResumed가 푼다.
                    _events.trySend(
                        RecordBattleEvent.Start(
                            Routes.BattleRunning(start.battleId, start.opponentName, start.distance.meters, kind.name)
                        )
                    )
                }

                is Outcome.Failure -> {
                    mutableState.update {
                        it.copy(isStarting = false, message = BattleErrorText.of(started.error))
                    }
                    if ((started.error as? AppError.Rejected)?.code == CELEBRITY_REMOVED) {
                        mutableState.update { it.copy(selected = null) }
                        load()
                    }
                }
            }
        }
    }

    private companion object {
        const val CELEBRITY_REMOVED = "CELEBRITY_REMOVED"
    }
}
