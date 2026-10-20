package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.wear.compose.material3.Text
import com.a207.runkk.watch.R

/**
 * 러닝 중 워치 화면(6.1.15, FR-042, 와이어프레임 6-23): 경과 시간·거리·현재 페이스·심박, 연결 끊김 표시.
 * 심박은 30초 넘게 없거나 수집 금지면 빈칸이다(규칙은 [WatchViewModel]). 화면은 상태를 그리기만 한다.
 */
@Composable
fun WatchRunDisplay(state: WatchUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WatchColors.Background)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.linkLost) {
            LinkLostChip()
            Spacer(Modifier.height(4.dp))
        }
        Text(text = WatchFormat.elapsed(state.elapsedSeconds), style = numberStyle(26.sp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = WatchFormat.distanceKm(state.distanceMeters), style = numberStyle(46.sp, WatchColors.Lime))
            Text(
                text = stringResource(R.string.unit_km),
                style = labelStyle(size = 13.sp),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
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
        Spacer(Modifier.height(4.dp))
        NoticeText(state.notice)
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
        WatchRunDisplay(
            state = WatchUiState(
                phoneConnected = true,
                running = true,
                elapsedSeconds = 1_234,
                distanceMeters = 4_321,
                paceSecondsPerKm = 315,
                heartRateBpm = 152
            )
        )
    }
}
