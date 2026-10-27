package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 대결 결과(FR-093·FR-103·FR-108): BattleRepository.result 비교표·판정(상대 표시명·도달 시간 차)·적립 리워드·이번 주 전적을 보인다.
 * 결과를 보인 뒤 BattleRunCoordinator.acknowledgeFinished를 불러 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 한다.
 */
@HiltViewModel
class BattleResultViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(BattleResultUiState())
    val state: StateFlow<BattleResultUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(battleId: Long, isForced: Boolean = false) {
        if (!isForced && _state.value.battleId == battleId && _state.value.error == null) return
        job?.cancel()
        _state.update { it.copy(isLoading = true, battleId = battleId, error = null) }
        job = viewModelScope.launch {
            show(battles.result(battleId))
            // 도착 직후에는 기록이 아직 올라가지 않아 보류로 온다. 서버는 판정을 앱에 알리지 않으므로 판정이 나올 때까지 다시 읽는다.
            // 다시 읽는 동안은 결과를 확인하는 화면을 보이고, 다시 읽기가 실패하면 그 화면을 그대로 둔다.
            try {
                repeat(PENDING_RETRIES) {
                    if (_state.value.result?.isPending != true) return@launch
                    _state.update { it.copy(isAwaitingVerdict = true) }
                    delay(PENDING_RETRY_MILLIS)
                    (battles.result(battleId) as? Outcome.Success)?.let(::show)
                }
            } finally {
                _state.update { it.copy(isAwaitingVerdict = false) }
            }
        }
    }

    private fun show(result: Outcome<BattleResult>) {
        when (result) {
            is Outcome.Success -> {
                _state.update { it.copy(isLoading = false, result = result.value) }
                coordinator.acknowledgeFinished()
            }

            is Outcome.Failure -> _state.update {
                it.copy(isLoading = false, error = BattleErrorText.of(result.error))
            }
        }
    }

    internal companion object {
        /** 보류 결과를 다시 읽는 간격과 횟수(3초 × 20 = 1분). 그 뒤에는 결과 다시 보기로 읽는다. */
        const val PENDING_RETRY_MILLIS = 3_000L
        const val PENDING_RETRIES = 20
    }
}
