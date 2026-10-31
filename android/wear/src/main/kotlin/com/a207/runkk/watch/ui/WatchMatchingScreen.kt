package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Distance

/**
 * 배틀런 매칭 중(FR-257, 피그마 BMA): 내 티어와 대결 거리, 기다린 시간, "상대를 찾고 있어요", 취소. 뒤로 가기도 취소와 같다(2.4.25).
 */
@Composable
fun WatchMatchingScreen(state: WatchFlow.Matching, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TrackBackground(isDimmed = true, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MatchHeader(state.tier, state.distance)
            Text(text = WatchFormat.elapsed(state.waitedSeconds), style = numberStyle(60.sp))
            Text(
                text = stringResource(R.string.matching),
                style = labelStyle(color = WatchColors.OnSurface, size = 14.sp).copy(fontWeight = FontWeight.Bold)
            )
            PillButton(
                text = stringResource(R.string.matching_cancel),
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp),
                tone = PillTone.OUTLINE,
                textSize = 12.sp
            )
        }
    }
}

/** 매칭 화면 머리줄(피그마 BMA·BMB): 티어 칩과 대결 거리. 티어를 모르면 거리만. */
@Composable
internal fun MatchHeader(tier: String?, distance: Distance) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tier?.let { TierChip(it) }
        Text(text = distanceLabel(distance), style = labelStyle(color = WatchColors.OnSurface, size = 12.sp))
    }
}
