package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.watch.R

/**
 * 워치 시작 화면(6.1.15, FR-042, N-21, 와이어프레임 6-22): 폰 연결 상태, 오늘 계획(거리·목표 페이스), 시작 버튼.
 * 시작을 눌러도 측정은 폰이 한다(FR-183, C-002). 화면은 상태를 그리기만 한다.
 */
@Composable
fun WatchHomeScreen(
    state: WatchUiState,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WatchColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConnectionStatus(
            connected = state.phoneConnected,
            label = stringResource(if (state.phoneConnected) R.string.phone_connected else R.string.phone_disconnected)
        )
        Spacer(Modifier.height(6.dp))
        TodayPlan(state)
        Spacer(Modifier.height(8.dp))
        PillButton(
            text = stringResource(if (state.startPending) R.string.start_pending else R.string.start),
            onClick = onStart,
            enabled = !state.startPending
        )
        Spacer(Modifier.height(4.dp))
        NoticeText(state.notice)
        if (state.heartRatePermissionNeeded) {
            Spacer(Modifier.height(4.dp))
            PillButton(text = stringResource(R.string.allow_heart_rate), onClick = onRequestPermission, primary = false)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = stringResource(R.string.record_on_phone), style = labelStyle(size = 10.sp))
    }
}

@Composable
private fun TodayPlan(state: WatchUiState) {
    Text(text = stringResource(R.string.today_plan), style = labelStyle(size = 11.sp))
    val planDistance = state.planDistanceMeters
    if (planDistance == null) {
        Text(
            text = stringResource(R.string.no_today_plan),
            style = labelStyle(color = WatchColors.OnSurface, size = 14.sp)
        )
        return
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = WatchFormat.distanceKm(planDistance), style = numberStyle(34.sp, WatchColors.Lime))
        Text(
            text = stringResource(R.string.unit_km),
            style = labelStyle(size = 12.sp),
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
    state.planPaceSecondsPerKm?.let { pace ->
        Text(
            text = stringResource(R.string.plan_target_pace, WatchFormat.pace(pace)),
            style = labelStyle(color = WatchColors.OnSurface, size = 12.sp)
        )
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchHomeScreenPreview() {
    WatchTheme {
        WatchHomeScreen(
            state = WatchUiState(phoneConnected = true, planDistanceMeters = 5_000, planPaceSecondsPerKm = 330),
            onStart = {},
            onRequestPermission = {}
        )
    }
}
