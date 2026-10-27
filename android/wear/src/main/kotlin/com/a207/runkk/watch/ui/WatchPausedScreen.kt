package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 일시정지 화면(와이어프레임 K4, FR-194): 폰 연결 상태, 일시정지 표시, 멈춘 거리(흐리게), "km, 경과 멈춤", 심박,
 * 아래 한 줄 조작(재개, 길게 눌러 종료). 지표는 폰이 정지 시작 때 값으로 고정해 보낸다.
 * 정지 중에도 종료(길게 누르기)는 그대로 받는다(FR-036, FR-181). 정지 자동 종료 안내는 폰이 보낸 안내로 보인다(FR-195).
 */
@Composable
fun WatchPausedScreen(state: WatchUiState, actions: WatchActions, modifier: Modifier = Modifier) {
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
            item { PausedBadge() }
            item {
                Text(text = WatchFormat.distanceKm(state.distanceMeters), style = numberStyle(46.sp, WatchColors.Muted))
            }
            item {
                Text(
                    text = stringResource(R.string.paused_distance_elapsed, WatchFormat.elapsed(state.elapsedSeconds)),
                    style = labelStyle(color = WatchColors.OnSurface, size = 12.sp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.paused_heart_rate, WatchFormat.heartRate(state.heartRateBpm)),
                    style = labelStyle(size = 11.sp)
                )
            }
            state.notice?.let { notice -> item { NoticeText(notice) } }
            item {
                RunActions(
                    onEndTapped = actions.onEndTapped,
                    onEndLongPressed = actions.onEndLongPressed,
                    modifier = Modifier.padding(top = 4.dp),
                    leading = if (state.pauseControl == PauseControl.RESUME) {
                        { ResumeButton(onResume = actions.onResumeTapped) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** 일시정지 아이콘과 "일시정지"(라임). */
@Composable
private fun PausedBadge() {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PauseIcon(color = WatchColors.Lime)
        Text(
            text = stringResource(R.string.paused),
            style = labelStyle(color = WatchColors.Lime, size = 14.sp).copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ResumeButton(onResume: () -> Unit) {
    PillButton(
        text = stringResource(R.string.resume),
        onClick = onResume,
        tone = PillTone.PRIMARY,
        textSize = ACTION_TEXT_SIZE,
        icon = { color -> PlayIcon(color = color, size = 10.dp) }
    )
}

/** 처음 가운데 둘 항목(거리). */
private const val DISTANCE_ITEM_INDEX = 2

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchPausedScreenPreview() {
    WatchTheme {
        WatchRoot(
            state = WatchUiState(
                isPhoneConnected = true,
                isRunning = true,
                elapsedSeconds = 1_421,
                distanceMeters = 4_121,
                heartRateBpm = 138,
                isPaused = true,
                isPausable = true
            ),
            actions = WatchActions.NONE
        )
    }
}
