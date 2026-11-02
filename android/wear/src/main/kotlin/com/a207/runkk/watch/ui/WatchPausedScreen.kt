package com.a207.runkk.watch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/**
 * 워치 단독 자유런 일시정지(FR-247, 피그마 FP∞·FP3): "일시정지", 그때까지의 누적 거리, "계속"(라임 원)과 "종료"(진한 원).
 * 종료는 1초 이상 눌러야 끝나고 짧게 누르면 "길게 눌러 종료"를 보인다(FR-248). 대결에는 이 화면이 없다(W-21).
 * 폰 러닝 동행 표시(자유런)도 이 화면을 쓴다(W35): 계속·종료는 폰에 요청하고, 폰이 보낸 안내(정지 자동 종료 예고, FR-195)는 [WatchFlow.Paused.banner]다.
 * 모션(W41): 측정 화면 위로 겹쳐 나타난 뒤(피그마 DISSOLVE 220ms) "일시정지"와 거리가 위에서 내려앉고 계속·종료 버튼이 작게
 * 시작해 커진다(320ms). 계속을 누르면 측정 화면이 다시 겹쳐 나타난다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchPausedScreen(
    state: WatchFlow.Paused,
    onResume: () -> Unit,
    onEndTapped: () -> Unit,
    onEndLongPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headIn = rememberReveal(order = 0)
    val actionsIn = rememberReveal(order = 1)
    TrackBackground(isDimmed = true, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.paused),
                modifier = Modifier.revealLayer(headIn, rise = -HEAD_DROP),
                style = labelStyle(color = WatchColors.OnSurface, size = 18.sp).copy(fontWeight = FontWeight.ExtraBold)
            )
            Row(
                modifier = Modifier.padding(top = 6.dp).revealLayer(headIn, rise = -HEAD_DROP),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(text = WatchFormat.distanceKm(state.progress.distance.meters), style = numberStyle(30.sp))
                Text(
                    text = stringResource(R.string.unit_km),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    style = numberStyle(18.sp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp).revealLayer(actionsIn, fromScale = ACTIONS_START_SCALE),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                RoundAction(
                    label = stringResource(R.string.paused_continue),
                    container = WatchColors.Lime,
                    onClick = onResume
                ) { PlayIcon(WatchColors.Ink, size = 18.dp) }
                RoundAction(
                    label = stringResource(R.string.paused_end),
                    container = WatchColors.Dark,
                    onClick = onEndTapped,
                    onLongClick = onEndLongPressed
                ) { StopIcon(WatchColors.OnSurface, size = 16.dp) }
            }
            if (state.notice != null) {
                FlowNoticeText(state.notice, modifier = Modifier.padding(top = 4.dp))
            } else if (state.banner != null) {
                Text(
                    text = state.banner,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    style = labelStyle(color = WatchColors.OnSurface, size = 11.sp).copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 둥근 큰 버튼과 아래 이름(피그마 FP3). 지름 64dp라 워치 최소 터치 크기(48dp)를 넘는다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundAction(
    label: String,
    container: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    val hold = stringResource(R.string.hold_to_end)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(ROUND_ACTION_SIZE)
                .clip(CircleShape)
                .background(container, CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = label,
                    onLongClickLabel = if (onLongClick != null) hold else null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) { icon() }
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = labelStyle(color = WatchColors.OnSurface, size = 11.sp).copy(fontWeight = FontWeight.Bold)
        )
    }
}

/** "일시정지"·거리가 내려앉는 거리와 버튼 처음 크기. */
private val HEAD_DROP = 8.dp
private const val ACTIONS_START_SCALE = 0.8f

/** 계속·종료 버튼 지름(피그마 FP3). */
private val ROUND_ACTION_SIZE = 56.dp
