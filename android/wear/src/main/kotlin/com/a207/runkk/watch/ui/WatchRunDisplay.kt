package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 러닝 중 워치 화면(6.1.15, FR-042, 와이어프레임 6-23): 경과 시간·거리·현재 페이스·심박, 연결 끊김 표시.
 * 심박은 30초 넘게 없거나 수집 금지면 빈칸이다(규칙은 [WatchViewModel]). 화면은 상태를 그리기만 한다.
 * ScalingLazyColumn이라 작은 화면에서 잘리는 지표는 크라운으로 내려 본다.
 */
@Composable
fun WatchRunDisplay(state: WatchUiState, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            if (state.linkLost) item { LinkLostChip() }
            item { Text(text = WatchFormat.elapsed(state.elapsedSeconds), style = numberStyle(26.sp)) }
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = WatchFormat.distanceKm(state.distanceMeters),
                        style = numberStyle(46.sp, WatchColors.Lime)
                    )
                    Text(
                        text = stringResource(R.string.unit_km),
                        style = labelStyle(size = 13.sp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
            state.notice?.let { notice -> item { NoticeText(notice) } }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = numberStyle(22.sp, color))
        Text(text = label, style = labelStyle(size = 10.sp))
    }
}

@Composable
private fun LinkLostChip() {
    val shape = RoundedCornerShape(50)
    Text(
        text = stringResource(R.string.link_lost),
        style = labelStyle(color = WatchColors.OnSurface, size = 11.sp),
        modifier = Modifier
            .background(WatchColors.Surface, shape)
            .border(1.dp, WatchColors.Border, shape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchRunDisplayPreview() {
    WatchTheme {
        WatchRoot(
            state = WatchUiState(
                phoneConnected = true,
                running = true,
                elapsedSeconds = 1_234,
                distanceMeters = 4_321,
                paceSecondsPerKm = 315,
                heartRateBpm = 152
            ),
            onStart = {},
            onRequestPermission = {}
        )
    }
}
