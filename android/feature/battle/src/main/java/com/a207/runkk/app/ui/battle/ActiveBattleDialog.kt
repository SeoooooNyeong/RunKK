package com.a207.runkk.app.ui.battle

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkActionDialog
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalBattleReturnRequest
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 앱 화면 위에 붙는 진행 중 대결 안내(배틀 QA 7번). 앱 그래프가 BattleFeatureEntry.Overlay로 로그인 뒤 한 번 붙인다.
 * 아직 달리는 대결로 돌아가기(S15P31A207-278): 대결 진행 알림 요청(LocalBattleReturnRequest)을 먼저 처리하고, 앱이 앞으로 오거나
 * 맨 위 화면([top])이 바뀔 때마다 돌아가기 안내 여부를 다시 본다. 열 화면은 RunkkNavigator로 연다.
 */
@Composable
fun ActiveBattleOverlay(top: NavKey?, viewModel: ActiveBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val request = LocalBattleReturnRequest.current
    LaunchedEffect(viewModel, navigator) { viewModel.returns.collect { navigator.navigate(it) } }
    // 알림 요청을 앞으로 옴 판정보다 먼저 처리한다(같은 프레임에 돌아가기 안내가 잠깐 뜨지 않게)
    DisposableEffect(request) {
        request?.let {
            viewModel.onReturnRequested(it.battleId)
            it.done()
        }
        onDispose {}
    }
    LifecycleStartEffect(top) {
        viewModel.onForeground(top)
        onStopOrDispose {}
    }
    ActiveBattleDialog(state, onForfeit = viewModel::onForfeit, onDismiss = viewModel::onDismiss)
    BattleReturnDialog(state.returnTo, onReturn = viewModel::onReturn, onDismiss = viewModel::onReturnDismissed)
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

/**
 * 아직 달리는 대결로 돌아가기 안내(S15P31A207-278): 제목은 남은 대결 안내와 같고 본문은 상대와 거리 한 줄, 버튼은
 * "대결로 돌아가기" 하나다. 뒤로가기·바깥 누름은 안내만 닫는다. [returnTo]가 없으면 그리지 않는다.
 */
@Composable
fun BattleReturnDialog(returnTo: Routes.BattleRunning?, onReturn: () -> Unit, onDismiss: () -> Unit) {
    val key = returnTo ?: return
    val distance = distanceLabel(Distance(key.distanceMeters)).asString()
    RunkkActionDialog(
        title = stringResource(R.string.battle_active_title),
        confirmLabel = stringResource(R.string.battle_active_return),
        onConfirm = onReturn,
        onDismissRequest = onDismiss
    ) {
        Text(
            stringResource(R.string.battle_active_message_name, key.opponentName, distance),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary
        )
    }
}
