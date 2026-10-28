package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 앱을 다시 켰을 때 서버에 남은 대결(배틀 QA 7번, "포기 안내 + 잠금 해제"). 앱 화면 위에 한 번 붙어(BattleFeatureEntry.Overlay)
 * GET /battles/active를 읽고, 이 프로세스가 맡지 않은 대결이면 그만두기를 권한다. 그만두면 포기로 끝나 새 대결을 시작할 수 있다.
 * 이어 달리기·기록 복구는 하지 않는다.
 */
@HiltViewModel
class ActiveBattleViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(ActiveBattleUiState())
    val state: StateFlow<ActiveBattleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { check() }
    }

    /** 읽지 못하거나 없으면(204) 아무것도 띄우지 않는다. 지금 달리고 있는 대결이면 그 화면이 맡는다. */
    private suspend fun check() {
        val active = (battles.active() as? Outcome.Success)?.value ?: return
        if (coordinator.isRunning(active.battleId)) return
        _state.update { it.copy(battle = active) }
    }

    fun onForfeit() {
        val battle = _state.value.battle ?: return
        if (_state.value.isForfeiting) return
        _state.update { it.copy(isForfeiting = true, error = null) }
        viewModelScope.launch {
            when (val forfeited = battles.forfeit(battle.battleId)) {
                is Outcome.Success -> _state.value = ActiveBattleUiState()

                is Outcome.Failure -> _state.update {
                    it.copy(isForfeiting = false, error = BattleErrorText.of(forfeited.error))
                }
            }
        }
    }

    fun onDismiss() {
        _state.value = ActiveBattleUiState()
    }
}
