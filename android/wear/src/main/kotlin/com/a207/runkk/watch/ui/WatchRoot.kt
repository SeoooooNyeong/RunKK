package com.a207.runkk.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 폰이 러닝 중이라고 알리면 [WatchRunDisplay], 아니면 [WatchHomeScreen]을 보인다. */
@Composable
fun WatchRoot(
    state: WatchUiState,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.running) {
        WatchRunDisplay(state = state, modifier = modifier)
    } else {
        WatchHomeScreen(
            state = state,
            onStart = onStart,
            onRequestPermission = onRequestPermission,
            modifier = modifier
        )
    }
}
