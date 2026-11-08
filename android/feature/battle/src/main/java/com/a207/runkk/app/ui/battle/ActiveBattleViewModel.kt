package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
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
 * 앱을 다시 켰을 때 서버에 남은 대결(배틀 QA 7번, "포기 안내 + 잠금 해제"). 앱 화면 위에 한 번 붙어(BattleFeatureEntry.Overlay)
 * GET /battles/active를 읽고, 이 프로세스가 맡지 않은 대결이면 끝내기를 권한다. 끝내면 새 대결을 시작할 수 있다.
 * 이 프로세스가 아직 달리고 있는 대결(최근 앱에서 밀어 닫아 Activity만 사라진 경우)은 끝내기 대신 진행 화면으로 돌려보낸다
 * (S15P31A207-278, "돌아가기 안내 + 알림으로 복귀"): 앱이 앞으로 올 때 맨 위가 그 진행 화면이 아니면 돌아가기를 안내하고, 대결 진행
 * 알림으로 열었으면 안내 없이 연다. 폰 진행 화면을 연 적 없는 대결(워치에서 시작한 대결)은 건드리지 않는다.
 * 셀프런·셀럽런은 취소(cancel, 승패·기록 없음)로, 그 밖은 그만두기(forfeit, 출발했으면 패)로 끝낸다(D-317, D-318, D-338, 코드 리뷰
 * AN10-2): 기기는 이미 기록을 버렸거나 잃었으므로 사용자가 고른 "기록 없이 취소"가 패배로 바뀌지 않게 한다.
 * 이어 달리기·기록 복구는 하지 않는다.
 */
@HiltViewModel
class ActiveBattleViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val screens: OpenBattleScreen
) : ViewModel() {
    private val _state = MutableStateFlow(ActiveBattleUiState())
    val state: StateFlow<ActiveBattleUiState> = _state.asStateFlow()

    private val _returns = Channel<Routes.BattleRunning>(Channel.BUFFERED)

    /** 열 대결 진행 화면. 앱 위 안내가 받아 그 화면으로 이동한다. */
    val returns: Flow<Routes.BattleRunning> = _returns.receiveAsFlow()

    /** 진행 화면으로 이동을 보냈고 아직 맨 위가 되지 않은 대결. 그 사이 돌아가기 안내를 다시 띄우지 않는다. */
    private var opening: Long? = null

    init {
        viewModelScope.launch { check() }
    }

    /** 읽지 못하거나 없으면(204) 아무것도 띄우지 않는다. 지금 달리고 있는 대결이면 그 화면이 맡는다. */
    private suspend fun check() {
        val active = (battles.active() as? Outcome.Success)?.value ?: return
        if (coordinator.isRunning(active.battleId)) return
        _state.update { it.copy(battle = active) }
    }

    /** 앱이 앞으로 왔거나 맨 위 화면이 바뀌었을 때. 열린 회가 있고 맨 위가 그 진행 화면이 아니면 돌아가기를 안내한다. */
    fun onForeground(top: NavKey?) {
        val key = screens.key()
        val isOnTop = key != null && (top as? Routes.BattleRunning)?.battleId == key.battleId
        if (isOnTop || key == null) opening = null
        val returnTo = key?.takeUnless { isOnTop || opening == it.battleId }
        _state.update { it.copy(returnTo = returnTo) }
    }

    /** 대결 진행 알림으로 열었을 때. 이 프로세스가 그 대결 회를 열고 있으면 안내 없이 진행 화면을 연다. 그 밖은 무시한다. */
    fun onReturnRequested(battleId: Long) {
        screens.key()?.takeIf { it.battleId == battleId }?.let(::open)
    }

    /** 돌아가기 안내의 "대결로 돌아가기". */
    fun onReturn() {
        _state.value.returnTo?.let(::open)
    }

    /** 돌아가기 안내를 뒤로가기·바깥 누름으로 닫았을 때. 다음에 앞으로 오면 다시 안내한다. */
    fun onReturnDismissed() = _state.update { it.copy(returnTo = null) }

    private fun open(key: Routes.BattleRunning) {
        opening = key.battleId
        _state.update { it.copy(returnTo = null) }
        _returns.trySend(key)
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
