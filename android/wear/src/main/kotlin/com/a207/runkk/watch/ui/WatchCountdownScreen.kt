package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/**
 * 출발 카운트다운(FR-180, FR-087, 피그마 FC3·BC1·SC3): 라임 원 바탕에 "자유런 3km" 머리줄과 큰 3·2·1, 마지막에 GO.
 * 머리줄에 가운뎃점을 넣지 않는다(사용자 문체 규칙).
 */
@Composable
fun WatchCountdownScreen(state: WatchFlow.Countdown, modifier: Modifier = Modifier) {
    val distance = state.distance?.let { distanceLabel(it) } ?: stringResource(R.string.unlimited)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchColors.Lime)
            .padding(5.dp)
            .border(3.dp, WatchColors.Ink, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.countdown_header, modeName(state.mode), distance),
                style = labelStyle(color = WatchColors.Ink, size = 13.sp).copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (state.secondsLeft >
                    0
                ) {
                    state.secondsLeft.toString()
                } else {
                    stringResource(R.string.countdown_go)
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                style = numberStyle(
                    if (state.secondsLeft >
                        0
                    ) {
                        110.sp
                    } else {
                        80.sp
                    },
                    WatchColors.Ink
                ).copy(fontStyle = FontStyle.Normal)
            )
        }
    }
}
