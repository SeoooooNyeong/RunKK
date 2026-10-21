package com.a207.runkk.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold

/**
 * 앱 전체 틀(AppScaffold: 시간 표시·화면 사이 계층 포커스). 폰이 러닝 중이라고 알리면 [WatchRunDisplay],
 * 아니면 [WatchHomeScreen]을 보인다. 두 화면은 ScreenScaffold + ScalingLazyColumn이라 크라운·베젤(로터리)로 스크롤한다.
 */
@Composable
fun WatchRoot(
    state: WatchUiState,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    onEndTapped: () -> Unit,
    onEndLongPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(modifier = modifier, containerColor = WatchColors.Background) {
        if (state.running) {
            WatchRunDisplay(state = state, onEndTapped = onEndTapped, onEndLongPressed = onEndLongPressed)
        } else {
            WatchHomeScreen(state = state, onStart = onStart, onRequestPermission = onRequestPermission)
        }
    }
}
