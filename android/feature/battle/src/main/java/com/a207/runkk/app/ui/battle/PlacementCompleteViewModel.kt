package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.PlacementStatus
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
 * 배치고사 완료(FR-281, D-501): 본인 배치 진행(AccountRepository.placement)의 확정 티어와 센 세 판을 보인다. 완료를 읽으면 마지막 판을
 * 본 것으로 기기에 기억한다(AccountRepository.savePlacementCompleteSeen, D-531: 배치고사 진행 화면이 다시 열지 않게).
 */
@HiltViewModel
class PlacementCompleteViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlacementCompleteUiState())
    val state: StateFlow<PlacementCompleteUiState> = _state.asStateFlow()

    private val _events = Channel<PlacementCompleteEvent>(Channel.BUFFERED)
    val events: Flow<PlacementCompleteEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onRandomBattle() {
        _events.trySend(PlacementCompleteEvent.RandomBattle)
    }

    fun onHome() {
        _events.trySend(PlacementCompleteEvent.Home)
    }

    /** 왼쪽 위 뒤로·시스템 뒤로는 「홈으로」와 같다(8절 10, D-501). */
    fun onBack() = onHome()

    private fun load() {
        if (job?.isActive == true) return
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val read = account.placement()) {
                is Outcome.Success -> {
                    val rounds = read.value.rounds.sortedBy { it.round }
                    if (read.value.status == PlacementStatus.COMPLETED) {
                        rounds.lastOrNull()?.let { account.savePlacementCompleteSeen(it.battleId) }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            tier = read.value.tier,
                            rounds = rounds,
                            wins = rounds.count { r -> r.verdict == Verdict.WIN.name },
                            losses = rounds.count { r -> r.verdict == Verdict.LOSE.name },
                            draws = rounds.count { r -> r.verdict == Verdict.DRAW.name }
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = UiText.Res(R.string.placement_load_failed))
                }
            }
        }
    }
}
