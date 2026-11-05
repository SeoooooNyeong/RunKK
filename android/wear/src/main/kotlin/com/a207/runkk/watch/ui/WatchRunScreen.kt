package com.a207.runkk.watch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.domain.run.RunProgress

/**
 * 워치 단독 측정 중(FR-246, FR-254, 피그마 FR∞·FR3·BR1·SR3): 가운데 큰 누적 거리, 그 아래 경과 시간과 현재 페이스, 아래 하트와 심박.
 * 위 줄은 대결이면 거리 차("+5m" 라임, "−6m" 주황), 자유런 목표가 있으면 "목표 3 km", 무제한이면 "LAP n"이다. 둘레 고리는 목표까지의
 * 진행률(무제한은 1km 구간 진행)이다. 안내 문구와 "역전!"은 가운데 띠로 잠깐 띄운다.
 *
 * 조작(피그마에 버튼이 없어 화면 전체로 받는다): 자유런은 누르면 일시정지, 대결은 누르면 "길게 눌러 종료"를 보이고, 모두 1초 이상
 * 누르면 끝낸다(FR-248). 저전력 표시([isAmbient])면 검은 바탕에 거리·시간만 그린다(D-382, 1분마다 다시 그림은 화면 밖이 정한다).
 * 모션(W41, 320ms): 거리 숫자는 새 값이 아래에서 올라오고, 고리는 새 채움으로 미끄러지고, 띠는 나타났다 흐려지며 사라진다. 고리 값은
 * 그리기 단계에서만 읽어 측정 1초 갱신마다 화면을 더 구성하지 않는다. 시간·페이스·심박은 움직이지 않는다(매초 바뀌어 배터리만 쓴다).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchRunScreen(
    state: WatchFlow.Running,
    onPause: () -> Unit,
    onEndTapped: () -> Unit,
    onEndLongPressed: () -> Unit,
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false
) {
    if (isAmbient) {
        AmbientRun(state.progress, modifier)
        return
    }
    val pauseLabel = stringResource(R.string.run_pause_action)
    val endLabel = stringResource(R.string.run_end_action)
    TrackBackground(
        isDimmed = true,
        modifier = modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = if (state.isPausable) pauseLabel else null,
            onLongClickLabel = endLabel,
            onClick = if (state.isPausable) onPause else onEndTapped,
            onLongClick = onEndLongPressed
        )
    ) {
        val meters = state.progress.distance.meters
        val goalMeters = state.goal?.meters?.takeIf { it > 0 }
        val ring = rememberRingFraction(
            goalMeters?.let { meters.toFloat() / it } ?: ((meters % LAP_METERS).toFloat() / LAP_METERS)
        )
        val ringColor = if (goalMeters != null) WatchColors.Lime else WatchColors.Lavender
        EdgeRing(fraction = { ring.value }, color = { ringColor }, hasStartKnob = true)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TopLine(state)
            Row {
                RollingNumber(
                    text = WatchFormat.distanceKm(meters),
                    modifier = Modifier.alignByBaseline(),
                    style = numberStyle(RUN_DISTANCE_SIZE)
                )
                Text(
                    text = stringResource(R.string.unit_km),
                    modifier = Modifier.alignByBaseline().padding(start = 5.dp),
                    style = numberStyle(RUN_UNIT_SIZE)
                )
            }
            Box(Modifier.padding(vertical = 4.dp).width(110.dp).height(1.dp).background(WatchColors.Ink))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(WatchFormat.elapsed(state.progress.elapsed.seconds), stringResource(R.string.label_time))
                Metric(
                    WatchFormat.pace(state.progress.currentPace?.secondsPerKm),
                    stringResource(R.string.label_run_pace)
                )
            }
            HeartRate(state.progress, Modifier.padding(top = 8.dp))
        }
        AnimatedBanner(
            when {
                state.isLeadChanged -> stringResource(R.string.lead_changed) to leadBannerColor(state)
                state.notice != null -> stringResource(noticeRes(state.notice)) to WatchColors.Lavender
                state.banner != null -> state.banner to WatchColors.Lavender
                state.isGpsSearching -> stringResource(R.string.gps_searching) to WatchColors.Lavender
                else -> null
            }
        )
    }
}

/**
 * 고리 채움을 [target]으로 부드럽게 옮긴다(320ms, W41). 무제한 구간 고리가 다 차서 0으로 돌아가면 끝까지 채운 뒤 0에서 다시 채운다.
 * 값은 그리기 단계에서만 읽는다([EdgeRing] 람다).
 */
@Composable
private fun rememberRingFraction(target: Float): Animatable<Float, AnimationVector1D> {
    val ring = remember { Animatable(target) }
    val still = LocalMotionStill.current
    LaunchedEffect(target, still) {
        if (still) {
            ring.snapTo(target)
            return@LaunchedEffect
        }
        val spec = tween<Float>(CAROUSEL_DURATION_MS, easing = CarouselEasing)
        if (isLapWrap(ring.value, target)) {
            ring.animateTo(1f, spec)
            ring.snapTo(0f)
        }
        ring.animateTo(target, spec)
    }
    return ring
}

/**
 * 바뀌면 새 값이 아래에서 올라오고 옛 값이 위로 빠지는 숫자(측정 거리, W41). 처음 그릴 때와 움직임 줄이기에서는 바로 바뀐다.
 * 움직임은 그리기 단계(graphicsLayer)에서만 한다. 배치를 옮기면 옆 "km"가 맞추는 기준선이 전환마다 흔들린다.
 * 빠지는 옛 값은 의미 트리에서 뺀다(WR16-3): 전환 중에 TalkBack이 옛 거리와 새 거리를 함께 읽지 않게.
 */
@Composable
private fun RollingNumber(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val still = LocalMotionStill.current
    var shown by remember { mutableStateOf(text) }
    var leaving by remember { mutableStateOf<String?>(null) }
    val roll = remember { Animatable(1f) }
    LaunchedEffect(text, still) {
        if (text == shown) return@LaunchedEffect
        if (still) {
            leaving = null
            shown = text
            roll.snapTo(1f)
            return@LaunchedEffect
        }
        leaving = shown
        shown = text
        roll.snapTo(0f)
        roll.animateTo(1f, tween(CAROUSEL_DURATION_MS, easing = CarouselEasing))
        leaving = null
    }
    Box(modifier) {
        leaving?.let { old ->
            Text(
                text = old,
                style = style,
                modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                    translationY = -size.height / ROLL_DIVISOR * roll.value
                    alpha = 1f - roll.value
                }
            )
        }
        Text(
            text = if (still) text else shown,
            style = style,
            modifier = Modifier.graphicsLayer {
                translationY = size.height / ROLL_DIVISOR * (1f - roll.value)
                alpha = roll.value
            }
        )
    }
}

/** 역전 띠 색. 방향을 알면 방향으로(앞질렀으면 라임, 따라잡혔으면 주황), 모르면(단독 측정) 거리 차 부호로 정한다. */
internal fun leadBannerColor(state: WatchFlow.Running): Color = when (state.leadKind) {
    LeadKind.OVERTOOK -> WatchColors.Lime
    LeadKind.OVERTAKEN -> WatchColors.Orange
    null -> if ((state.gapMeters ?: 0) >= 0) WatchColors.Lime else WatchColors.Orange
}

/** 위 줄: 대결 거리 차, 자유런 목표, 무제한 구간 번호. */
@Composable
private fun TopLine(state: WatchFlow.Running) {
    val gap = state.gapMeters
    val goal = state.goal
    when {
        state.mode != WatchMode.FREE && gap != null -> {
            // 앞서고 뒤처짐이 바뀌면 라임과 주황 사이를 320ms 동안 옮긴다(W41).
            val color by animateColorAsState(
                if (gap >= 0) WatchColors.Lime else WatchColors.Orange,
                motionSpec(CAROUSEL_DURATION_MS, CarouselEasing),
                label = "gap-color"
            )
            Text(
                text = if (gap >= 0) {
                    stringResource(R.string.battle_gap_ahead, gap)
                } else {
                    stringResource(R.string.battle_gap_behind, -gap)
                },
                style = numberStyle(20.sp, color)
            )
        }

        state.mode != WatchMode.FREE -> Text(text = " ", style = numberStyle(20.sp))

        goal != null -> DotLabel(stringResource(R.string.run_goal, kmNumber(goal)))

        else -> DotLabel(stringResource(R.string.run_lap, state.progress.distance.meters / LAP_METERS + 1))
    }
}

/** 라임 점과 작은 글자(피그마 FR3 "● 목표 3 km", FR∞ "● LAP 1"). */
@Composable
private fun DotLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(WatchColors.Lime, CircleShape))
        Text(
            text = text,
            style = labelStyle(color = WatchColors.OnSurface, size = 11.sp).copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(text = value, style = numberStyle(RUN_VALUE_SIZE, weight = FontWeight.Bold))
        Text(text = label, style = labelStyle(color = WatchColors.OnSurface, size = 9.sp))
    }
}

/** 하트와 심박. 심박이 없으면 칸을 비운다(FR-246). */
@Composable
internal fun HeartRate(progress: RunProgress, modifier: Modifier = Modifier) {
    val bpm = progress.heartRate?.bpm
    val label = stringResource(R.string.paused_heart_rate, WatchFormat.heartRate(bpm))
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (bpm != null) {
            HeartIcon(WatchColors.Heart, Modifier.clearAndSetSemantics {})
            Text(
                text = bpm.toString(),
                modifier = Modifier.clearAndSetSemantics {
                },
                style = numberStyle(14.sp, WatchColors.Heart)
            )
        }
    }
}

/** 저전력 표시: 검은 바탕, 흰 거리와 시간만(D-382). 번인 방지를 위해 채운 면이 없다. */
@Composable
private fun AmbientRun(progress: RunProgress, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = WatchFormat.distanceKm(progress.distance.meters), style = numberStyle(44.sp, Color.White))
            Text(
                text = stringResource(R.string.unit_km),
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                style = numberStyle(20.sp, Color.White)
            )
        }
        Text(text = WatchFormat.elapsed(progress.elapsed.seconds), style = numberStyle(20.sp, Color.White))
    }
}

/** 피그마 FR3 글자 크기(300px 화면 ×0.64): 거리 81px, km 41px, 시간·페이스 30px. */
private val RUN_DISTANCE_SIZE = 52.sp
private val RUN_UNIT_SIZE = 26.sp
private val RUN_VALUE_SIZE = 19.sp

/** 숫자가 굴러 들어오는 거리(글자 높이의 이 몫). */
private const val ROLL_DIVISOR = 2

/** 무제한 고리 한 바퀴(1km 구간, FR-246 제안). */
private const val LAP_METERS = 1_000L
