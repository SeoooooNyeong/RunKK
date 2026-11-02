package com.a207.runkk.watch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 모션(W41, 피그마에 안쪽 모션이 없어 메인과 같은 320ms 곡선): 숫자가 바뀌면 새 숫자가 크게 들어와 줄고 옛 숫자는 작아지며 사라진다.
 * 숫자마다 둘레 원이 12시에서 시계 방향으로 1초 동안 다시 차오르고, GO는 다 찬 원이다. 움직임 줄이기에서는 바로 바뀐다.
 */
@Composable
fun WatchCountdownScreen(state: WatchFlow.Countdown, modifier: Modifier = Modifier) {
    val distance = state.distance?.let { distanceLabel(it) } ?: stringResource(R.string.unlimited)
    val still = LocalMotionStill.current
    val fill = remember { Animatable(if (still || state.secondsLeft <= 0) 1f else 0f) }
    LaunchedEffect(state.secondsLeft, still) {
        if (still || state.secondsLeft <= 0) {
            fill.snapTo(1f)
        } else {
            fill.snapTo(0f)
            fill.animateTo(1f, tween(COUNTDOWN_FILL_MS, easing = LinearEasing))
        }
    }
    // 다 찬 원은 원래 테두리로 그리고, 차오르는 동안만 호를 그린다(차오름 시작·끝에만 다시 구성한다).
    val isFull by remember { derivedStateOf { fill.value >= 1f } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchColors.Lime)
            .padding(5.dp)
            .then(
                if (isFull) {
                    Modifier.border(RING_WIDTH, WatchColors.Ink, CircleShape)
                } else {
                    Modifier.drawBehind {
                        val stroke = RING_WIDTH.toPx()
                        drawArc(
                            color = WatchColors.Ink,
                            startAngle = RING_TOP,
                            sweepAngle = FULL_TURN * fill.value,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.countdown_header, modeName(state.mode), distance),
                style = labelStyle(color = WatchColors.Ink, size = 13.sp).copy(fontWeight = FontWeight.Bold)
            )
            AnimatedContent(
                targetState = state.secondsLeft,
                transitionSpec = {
                    if (still) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val spec = tween<Float>(CAROUSEL_DURATION_MS, easing = CarouselEasing)
                        (scaleIn(spec, ENTER_SCALE) + fadeIn(spec)) togetherWith
                            (scaleOut(spec, EXIT_SCALE) + fadeOut(spec))
                    } using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                label = "countdown-number"
            ) { left ->
                Text(
                    text = if (left > 0) left.toString() else stringResource(R.string.countdown_go),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    style = numberStyle(if (left > 0) 110.sp else 80.sp, WatchColors.Ink)
                        .copy(fontStyle = FontStyle.Normal)
                )
            }
        }
    }
}

/** 둘레 원 두께(피그마 FC3), 차오름 시작 각도(12시)와 한 바퀴. */
private val RING_WIDTH = 3.dp
private const val RING_TOP = -90f
private const val FULL_TURN = 360f

/** 새 숫자는 이 크기에서 줄어 들어오고, 옛 숫자는 이 크기로 줄며 사라진다. */
private const val ENTER_SCALE = 1.4f
private const val EXIT_SCALE = 0.6f
