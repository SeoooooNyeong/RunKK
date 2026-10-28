package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 워치 시작 화면(와이어프레임 K1, 6.1.15, FR-042, N-21): 폰 연결 상태, 오늘 계획(거리, 목표 페이스 한 줄), 가운데 원형 시작 버튼과
 * 그 아래 "자유런". 시작 요청을 보낸 뒤에는 버튼을 잠그고 "자유런" 자리에 "폰에서 시작하는 중"을 보인다.
 * 폰과 연결되어 있지 않으면 이 화면 대신 [WatchNoLinkScreen](K3)이다. 시작을 눌러도 측정은 폰이 한다(FR-183, C-002).
 */
@Composable
fun WatchHomeScreen(state: WatchUiState, actions: WatchActions, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            homeItems(state, actions)
        }
    }
}

/**
 * 시작 화면 목록: 연결 상태, 오늘 계획, 시작 버튼, 자유런을 한 덩어리로 가운데 두고(와이어프레임 K1), 그 아래 안내와 심박 권한 버튼.
 * 덩어리로 묶어야 원형 화면 가운데에 시작 버튼이 오고 위아래가 잘리지 않는다.
 */
private fun ScalingLazyListScope.homeItems(state: WatchUiState, actions: WatchActions) {
    item {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ConnectionStatus(isConnected = state.isPhoneConnected, label = stringResource(R.string.phone_connected))
            TodayPlan(state)
            StartButton(isStartPending = state.isStartPending, onStart = actions.onStart)
            Text(
                text = stringResource(if (state.isStartPending) R.string.start_pending else R.string.free_run),
                style = labelStyle(size = 11.sp),
                textAlign = TextAlign.Center
            )
        }
    }
    state.notice?.let { notice -> item { NoticeText(notice) } }
    if (state.isHeartRatePermissionNeeded) {
        item {
            PillButton(
                text = stringResource(R.string.allow_heart_rate),
                onClick = actions.onRequestPermission,
                tone = PillTone.OUTLINE
            )
        }
    }
}

/** "오늘 계획" 라벨과 계획 한 줄("5.00km, 6'00"/km"). 계획이 없으면 "오늘 계획 없음". */
@Composable
private fun TodayPlan(state: WatchUiState) {
    val planDistance = state.planDistanceMeters
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (planDistance == null) {
            Text(
                text = stringResource(R.string.no_today_plan),
                style = labelStyle(color = WatchColors.OnSurface, size = 13.sp)
            )
            return@Column
        }
        Text(text = stringResource(R.string.today_plan), style = labelStyle(size = 11.sp))
        val distance = WatchFormat.distanceKm(planDistance)
        val pace = state.planPaceSecondsPerKm
        Text(
            text = if (pace == null) {
                stringResource(R.string.plan_distance, distance)
            } else {
                stringResource(R.string.plan_distance_pace, distance, WatchFormat.pace(pace))
            },
            style = numberStyle(17.sp)
        )
    }
}

/** 가운데 원형 시작 버튼(라임). 시작 요청을 보낸 뒤 응답 전에는 누를 수 없다. */
@Composable
private fun StartButton(isStartPending: Boolean, onStart: () -> Unit) {
    val isEnabled = !isStartPending
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(START_BUTTON_SIZE)
            .clip(CircleShape)
            .background(if (isEnabled) WatchColors.Lime else WatchColors.Surface)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onStart),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.start),
            style = labelStyle(color = if (isEnabled) WatchColors.Background else WatchColors.Muted, size = 18.sp)
                .copy(fontWeight = FontWeight.Bold)
        )
    }
}

/** 원형 시작 버튼 지름(와이어프레임 K1: 화면 지름의 약 3분의 1). */
private val START_BUTTON_SIZE = 60.dp

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchHomeScreenPreview() {
    WatchTheme {
        WatchRoot(
            state = WatchUiState(isPhoneConnected = true, planDistanceMeters = 5_000, planPaceSecondsPerKm = 360),
            actions = WatchActions.NONE
        )
    }
}
