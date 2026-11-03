package com.a207.runkk.watch.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Distance

/**
 * 배틀런 매칭 중(FR-257, 피그마 BMA): 내 티어와 대결 거리, 기다린 시간, "상대를 찾고 있어요", 취소. 뒤로 가기도 취소와 같다(2.4.25).
 * 모션(W41): 피그마 BMA에는 안쪽 모션이 없어, 기다리는 동안 "상대를 찾고 있어요"만 천천히 흐려졌다 또렷해진다(숨쉬기). 움직임
 * 줄이기에서는 멈춰 있다.
 */
@Composable
fun WatchMatchingScreen(state: WatchFlow.Matching, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val breath = rememberBreath()
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
                modifier = Modifier.graphicsLayer { alpha = breath?.value ?: 1f },
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

/** 기다리는 동안 흐려졌다 또렷해지기를 되풀이하는 흐림 값. 움직임 줄이기면 null(늘 또렷함)이다. 그리기 층에서만 읽는다. */
@Composable
private fun rememberBreath(): State<Float>? {
    if (LocalMotionStill.current) return null
    return rememberInfiniteTransition(label = "matching").animateFloat(
        initialValue = 1f,
        targetValue = BREATH_LOW_ALPHA,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = CarouselEasing), RepeatMode.Reverse),
        label = "matching-breath"
    )
}

/** 숨쉬기 반 주기와 가장 흐린 정도. */
private const val BREATH_MS = 900
private const val BREATH_LOW_ALPHA = 0.45f

/** 매칭 화면 머리줄(피그마 BMA·BMB): 티어 칩과 대결 거리. 티어를 모르면 거리만. */
@Composable
internal fun MatchHeader(tier: String?, distance: Distance) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tier?.let { TierChip(it) }
        Text(text = distanceLabel(distance), style = labelStyle(color = WatchColors.OnSurface, size = 12.sp))
    }
}
