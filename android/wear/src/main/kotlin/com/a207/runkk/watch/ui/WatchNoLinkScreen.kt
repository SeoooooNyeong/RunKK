package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 폰 미연결 화면(와이어프레임 K3, FR-183): 러닝 전인데 폰과 연결되어 있지 않으면 시작 화면 대신 보인다.
 * 빈 점 "폰 연결 안 됨", 본문 한 줄, "다시 연결"(누르면 연결을 바로 다시 확인하고, 연결되면 시작 화면으로 바뀐다).
 */
@Composable
fun WatchNoLinkScreen(onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            item { ConnectionStatus(isConnected = false, label = stringResource(R.string.phone_disconnected)) }
            item {
                Text(
                    text = stringResource(R.string.no_link_title),
                    style = labelStyle(color = WatchColors.OnSurface, size = 16.sp).copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            item {
                PillButton(
                    text = stringResource(R.string.reconnect),
                    onClick = onReconnect,
                    tone = PillTone.OUTLINE,
                    textSize = 13.sp
                )
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchNoLinkScreenPreview() {
    WatchTheme {
        WatchRoot(state = WatchUiState(isPhoneConnected = false), actions = WatchActions.NONE)
    }
}
