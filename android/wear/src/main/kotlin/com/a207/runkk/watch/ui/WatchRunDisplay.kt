package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 러닝 중 워치 화면(와이어프레임 K2, 6.1.15, FR-042): 폰 연결 상태, 경과 시간, 큰 거리와 km, 현재 페이스·심박,
 * 아래 한 줄 조작(자유런이면 일시정지, 그리고 길게 눌러 종료).
 * 심박은 30초 넘게 없거나 수집 금지면 빈칸이다(규칙은 [WatchViewModel]). 화면은 상태를 그리기만 한다.
 * 종료는 길게 눌러야 폰에 종료를 요청한다(FR-181). 일시정지 버튼은 폰이 일시정지할 수 있다고 알린 자유런에만 둔다(FR-194).
 * 정지 중이면 [WatchPausedScreen](K4), 대결 근접 알림 중이면 [WatchBattleScreen](K5)이 이 화면 대신 나온다.
 */
@Composable
fun WatchRunDisplay(state: WatchUiState, actions: WatchActions, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = DISTANCE_ITEM_INDEX)
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            item { RunConnectionStatus(isLinkLost = state.isLinkLost) }
            item {
                Text(text = WatchFormat.elapsed(state.elapsedSeconds), style = numberStyle(16.sp, WatchColors.Muted))
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = WatchFormat.distanceKm(state.distanceMeters),
                        style = numberStyle(50.sp, WatchColors.Lime)
                    )
                    Text(text = stringResource(R.string.unit_km), style = labelStyle(size = 12.sp))
                }
            }
            item { PaceAndHeartRate(state) }
            state.notice?.let { notice -> item { NoticeText(notice) } }
            item {
                RunActions(
                    onEndTapped = actions.onEndTapped,
                    onEndLongPressed = actions.onEndLongPressed,
                    modifier = Modifier.padding(top = 4.dp),
                    leading = if (state.pauseControl == PauseControl.PAUSE) {
                        { PauseButton(onPause = actions.onPauseTapped) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** 러닝 화면 맨 위 연결 상태("폰 연결됨" 또는 "폰 연결 끊김"). 일시정지 화면도 같이 쓴다. */
@Composable
internal fun RunConnectionStatus(isLinkLost: Boolean) {
    ConnectionStatus(
        isConnected = !isLinkLost,
        label = stringResource(if (isLinkLost) R.string.link_lost else R.string.phone_connected)
    )
}

/** 일시정지(와이어프레임 K2). 작은 원형 화면에 "길게 눌러 종료"와 한 줄로 두려고 아이콘만 보이고 "일시정지"는 읽어 준다. */
@Composable
private fun PauseButton(onPause: () -> Unit) {
    RoundIconButton(contentDescription = stringResource(R.string.pause), onClick = onPause) { color ->
        PauseIcon(color = color, size = 14.dp)
    }
}

@Composable
private fun PaceAndHeartRate(state: WatchUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Metric(
            value = WatchFormat.pace(state.paceSecondsPerKm),
            label = stringResource(R.string.label_pace),
            color = WatchColors.OnSurface
        )
        Metric(
            value = WatchFormat.heartRate(state.heartRateBpm),
            label = stringResource(R.string.label_heart_rate),
            color = WatchColors.Lavender
        )
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = numberStyle(22.sp, color))
        Text(text = label, style = labelStyle(size = 10.sp))
    }
}

/** 처음 가운데 둘 항목(거리). */
private const val DISTANCE_ITEM_INDEX = 2

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchRunDisplayPreview() {
    WatchTheme {
        WatchRoot(
            state = WatchUiState(
                isPhoneConnected = true,
                isRunning = true,
                elapsedSeconds = 1_421,
                distanceMeters = 4_121,
                paceSecondsPerKm = 342,
                heartRateBpm = 152,
                isPausable = true
            ),
            actions = WatchActions.NONE
        )
    }
}
