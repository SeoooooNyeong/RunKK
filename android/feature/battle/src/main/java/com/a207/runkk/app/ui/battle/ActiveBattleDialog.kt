package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.asString

/** 앱 화면 위에 붙는 진행 중 대결 안내(배틀 QA 7번). 앱 그래프가 BattleFeatureEntry.Overlay로 로그인 뒤 한 번 붙인다. */
@Composable
fun ActiveBattleOverlay(viewModel: ActiveBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActiveBattleDialog(state, onForfeit = viewModel::onForfeit, onDismiss = viewModel::onDismiss)
}

/** "진행 중인 대결이 있어요": 본문은 상대와 거리 한 줄(그만두기가 실패하면 그 문구). 대결이 없으면 그리지 않는다. */
@Composable
fun ActiveBattleDialog(state: ActiveBattleUiState, onForfeit: () -> Unit, onDismiss: () -> Unit) {
    val battle = state.battle ?: return
    val distance = distanceLabel(battle.distance).asString()
    val message = state.error?.asString()
        ?: battle.opponentName?.let { stringResource(R.string.battle_active_message_name, it, distance) }
        ?: stringResource(R.string.battle_active_message, distance)
    RunkkConfirmDialog(
        title = stringResource(R.string.battle_active_title),
        message = message,
        // 셀프런·셀럽런은 취소로 끝낸다(D-338): 확인 단추도 "취소하기"
        confirmLabel =
            stringResource(
                if (battle.isCancellable) R.string.battle_cancel_confirm else R.string.battle_active_forfeit
            ),
        onConfirm = onForfeit,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.battle_active_close),
        isDestructive = true
    )
}
