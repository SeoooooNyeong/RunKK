package com.a207.runkk.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText

/**
 * 앱 전체 틀(AppScaffold: 시간 표시·화면 사이 계층 포커스). 화면은 상태로 고른다(와이어프레임 K1~K5):
 * - 러닝 중: 정지 중이면 [WatchPausedScreen](K4), 대결 근접 알림 중이면 [WatchBattleScreen](K5), 아니면 [WatchRunDisplay](K2).
 * - 러닝 전: 폰과 연결되어 있으면 [WatchHomeScreen](K1), 아니면 [WatchNoLinkScreen](K3).
 * 맨 위 시각은 [timeText](스크린샷 시험은 고정 시각을 넣는다). 모든 화면은 ScreenScaffold + ScalingLazyColumn이라 크라운·베젤(로터리)로 스크롤한다.
 */
@Composable
fun WatchRoot(
    state: WatchUiState,
    actions: WatchActions,
    modifier: Modifier = Modifier,
    timeText: @Composable () -> Unit = { TimeText() }
) {
    AppScaffold(modifier = modifier, timeText = timeText, containerColor = WatchColors.Background) {
        when {
            state.isRunning && state.isPaused -> WatchPausedScreen(state = state, actions = actions)
            state.isRunning && state.proximity != null -> WatchBattleScreen(state = state, proximity = state.proximity)
            state.isRunning -> WatchRunDisplay(state = state, actions = actions)
            state.isPhoneConnected -> WatchHomeScreen(state = state, actions = actions)
            else -> WatchNoLinkScreen(onReconnect = actions.onReconnect)
        }
    }
}
