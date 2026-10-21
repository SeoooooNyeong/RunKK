package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 워치 시작 화면(6.1.15, FR-042, N-21, 와이어프레임 6-22): 폰 연결 상태, 오늘 계획(거리·목표 페이스), 시작 버튼.
 * 시작은 화면 아래 가장자리 버튼(EdgeButton)이다. 목록은 ScalingLazyColumn이라 작은 화면에서도 크라운으로 내려 본다.
 * 시작을 눌러도 측정은 폰이 한다(FR-183, C-002). 화면은 상태를 그리기만 한다.
 */
@Composable
fun WatchHomeScreen(
    state: WatchUiState,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        edgeButton = { StartEdgeButton(isStartPending = state.startPending, onStart = onStart) }
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            homeItems(state, onRequestPermission)
        }
    }
}

/** 화면 아래 가장자리 시작 버튼. 시작 요청을 보낸 뒤 응답 전에는 누를 수 없다. */
@Composable
private fun StartEdgeButton(isStartPending: Boolean, onStart: () -> Unit) {
    EdgeButton(
        onClick = onStart,
        enabled = !isStartPending,
        colors = ButtonDefaults.buttonColors(
            containerColor = WatchColors.Lime,
            contentColor = WatchColors.Background,
            disabledContainerColor = WatchColors.Surface,
            disabledContentColor = WatchColors.Muted
        )
    ) {
        Text(
            text = stringResource(if (isStartPending) R.string.start_pending else R.string.start),
            textAlign = TextAlign.Center
        )
    }
}

/** 시작 화면 목록: 연결 상태, 오늘 계획, 안내, 심박 권한 버튼, 기록 안내 순서. */
private fun ScalingLazyListScope.homeItems(state: WatchUiState, onRequestPermission: () -> Unit) {
    item {
        ConnectionStatus(
            connected = state.phoneConnected,
            label = stringResource(if (state.phoneConnected) R.string.phone_connected else R.string.phone_disconnected)
        )
    }
    todayPlan(state)
    state.notice?.let { notice -> item { NoticeText(notice) } }
    if (state.heartRatePermissionNeeded) {
        item {
            PillButton(
                text = stringResource(R.string.allow_heart_rate),
                onClick = onRequestPermission,
                primary = false
            )
        }
    }
    item { Text(text = stringResource(R.string.record_on_phone), style = labelStyle(size = 10.sp)) }
}

private fun ScalingLazyListScope.todayPlan(state: WatchUiState) {
    item { Text(text = stringResource(R.string.today_plan), style = labelStyle(size = 11.sp)) }
    val planDistance = state.planDistanceMeters
    if (planDistance == null) {
        item {
            Text(
                text = stringResource(R.string.no_today_plan),
                style = labelStyle(color = WatchColors.OnSurface, size = 14.sp)
            )
        }
        return
    }
    item {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = WatchFormat.distanceKm(planDistance), style = numberStyle(34.sp, WatchColors.Lime))
            Text(
                text = stringResource(R.string.unit_km),
                style = labelStyle(size = 12.sp),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
    state.planPaceSecondsPerKm?.let { pace ->
        item {
            Text(
                text = stringResource(R.string.plan_target_pace, WatchFormat.pace(pace)),
                style = labelStyle(color = WatchColors.OnSurface, size = 12.sp)
            )
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchHomeScreenPreview() {
    WatchTheme {
        WatchRoot(
            state = WatchUiState(phoneConnected = true, planDistanceMeters = 5_000, planPaceSecondsPerKm = 330),
            onStart = {},
            onRequestPermission = {},
            onEndTapped = {},
            onEndLongPressed = {}
        )
    }
}
