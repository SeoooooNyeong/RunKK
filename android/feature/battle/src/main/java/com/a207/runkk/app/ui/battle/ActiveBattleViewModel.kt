package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
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
 * GET /battles/active를 읽고, 이 프로세스가 맡지 않은 대결이면 끝내기를 권한다. 끝내면 새 대결을 시작할 수 있다.
 * 셀프런·셀럽런은 취소(cancel, 승패·기록 없음)로, 그 밖은 그만두기(forfeit, 출발했으면 패)로 끝낸다(D-317, D-318, D-338, 코드 리뷰
 * AN10-2): 기기는 이미 기록을 버렸거나 잃었으므로 사용자가 고른 "기록 없이 취소"가 패배로 바뀌지 않게 한다.
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
            val ended = if (battle.isCancellable) battles.cancel(battle.battleId) else battles.forfeit(battle.battleId)
            when (ended) {
                is Outcome.Success -> _state.value = ActiveBattleUiState()

                is Outcome.Failure -> _state.update {
                    it.copy(isForfeiting = false, error = BattleErrorText.of(ended.error))
                }
            }
        }
    }

    fun onDismiss() {
        _state.value = ActiveBattleUiState()
    }
}

/** 기록 상대 대결(셀프런·셀럽런)이라 취소로 끝내는가(D-317: 서버 BattleType.allowsCancel과 같은 두 종류). */
internal val ActiveBattle.isCancellable: Boolean
    get() = type == BattleHistory.Type.SELF || type == BattleHistory.Type.CELEBRITY
