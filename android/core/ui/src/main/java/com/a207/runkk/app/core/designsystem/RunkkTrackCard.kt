package com.a207.runkk.app.core.designsystem

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 트랙 보기 카드([임시:D-성-256], [임시:D-성-313], [임시:FR-성-27], 시안 320×300 모서리 24). 아래에서부터 거리 자, 내 레인, 상대 레인을
 * 놓고 그 뒤에 [backdrop](내가 고른 아바타 무대 배경)을 깐다. 가로 위치는 [layout]이 정한다: 내 아바타([myAvatar])는 폭의 40%, 상대
 * ([otherAvatar])는 거리 차만큼 앞뒤, 화면 밖이면 그리지 않는다. 결승선은 [layout]에 있을 때만 그린다([임시:D-성-306]). 상대 위치는 300 ms
 * 안에 따라간다([임시:D-성-273], 움직임 줄이기면 바로 옮긴다).
 *
 * 이름표는 아바타 머리 위에 [myTag](라임 바탕)·[otherTag](표면색 바탕, null이면 없음)와 현재 페이스([myPace]·[otherPace]), 얼굴 원
 * ([myFace]·[otherFace], 20dp 칸)을 적는다. 가장자리 칩 [aheadChip](오른쪽)·[behindChip](왼쪽)과 거리 자 가운데 알약 [rulerLabel]은
 * 글자를 부르는 쪽이 만든다(대결 「바람처럼 +240 m」, 크루런 「앞 2명 ›」). 진행 막대([RunkkTrackProgressBar])와 보기 전환
 * ([RunkkViewModeToggle])은 이 부품에 없다: 트랙과 지도 가운데 무엇을 그릴지는 화면이 고르고, 둘을 카드 위에 겹쳐 놓는다.
 * 아바타와 배경은 람다로 받는다(이 패키지는 core.navigation의 칸을 import할 수 없다).
 */
@Composable
fun RunkkTrackCard(
    layout: TrackLayout.Result,
    myTag: String,
    otherTag: String?,
    myAvatar: @Composable () -> Unit,
    otherAvatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    myPace: String? = null,
    otherPace: String? = null,
    aheadChip: String? = null,
    behindChip: String? = null,
    rulerLabel: String? = null,
    myFace: (@Composable () -> Unit)? = null,
    otherFace: (@Composable () -> Unit)? = null,
    backdrop: @Composable () -> Unit = {}
) {
    LogDrawn(layout)
    val onTrack = layout.other as? TrackLayout.Other.OnTrack
    val reduceMotion = LocalReduceMotion.current
    // 상대가 트랙에 들어올 때는 내 자리에서 미끄러져 오지 않게 바로 놓고, 트랙 위에서 움직일 때만 따라간다.
    val otherX by animateFloatAsState(
        targetValue = onTrack?.x ?: layout.myX,
        animationSpec = if (reduceMotion || onTrack == null) snap() else tween(OTHER_MOVE_MILLIS),
        label = "trackOtherX"
    )
    BoxWithConstraints(
        modifier = modifier
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
    ) {
        val width = maxWidth
        Box(Modifier.fillMaxSize()) { backdrop() }
        Lanes(Modifier.align(Alignment.BottomCenter))
        layout.finish?.let { finish ->
            FinishLine(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = width * finish.x - FINISH_WIDTH / 2, y = -RULER_HEIGHT)
                    .testTag(TRACK_FINISH_TAG)
            )
        }
        if (onTrack != null) {
            Runner(
                tag = otherTag,
                pace = otherPace,
                isMine = false,
                face = otherFace,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = width * otherX - OTHER_SIZE / 2, y = -(RULER_HEIGHT + LANE_HEIGHT + FOOT_LIFT))
                    .testTag(TRACK_OTHER_TAG),
                size = OTHER_SIZE,
                avatar = otherAvatar
            )
        }
        Runner(
            tag = myTag,
            pace = myPace,
            isMine = true,
            face = myFace,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = width * layout.myX - MY_SIZE / 2, y = -(RULER_HEIGHT + FOOT_LIFT))
                .testTag(TRACK_ME_TAG),
            size = MY_SIZE,
            avatar = myAvatar
        )
        behindChip?.let {
            EdgeChip(
                it,
                Modifier.align(
                    Alignment.BottomStart
                ).padding(start = 8.dp, bottom = CHIP_BOTTOM).testTag(TRACK_BEHIND_TAG)
            )
        }
        aheadChip?.let {
            EdgeChip(
                it,
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = CHIP_BOTTOM).testTag(TRACK_AHEAD_TAG)
            )
        }
        Ruler(
            myX = layout.myX,
            otherX = onTrack?.let { otherX },
            label = rulerLabel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 달리는 사람 한 명: 머리 위 이름표와 아바타 칸([size] 정사각형, 발이 칸 바닥). 이름표는 칸보다 넓어도 가운데를 맞춘다. */
@Composable
private fun Runner(
    tag: String?,
    pace: String?,
    isMine: Boolean,
    face: (@Composable () -> Unit)?,
    modifier: Modifier,
    size: Dp,
    avatar: @Composable () -> Unit
) {
    Column(modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        if (tag != null) NameTag(tag, pace, isMine, face)
        Box(Modifier.size(size)) { avatar() }
    }
}

/** 이름표: 얼굴 원 20, 이름, 현재 페이스. 내 이름표는 라임 바탕, 상대는 표면색 바탕이다. */
@Composable
private fun NameTag(name: String, pace: String?, isMine: Boolean, face: (@Composable () -> Unit)?) {
    val background = if (isMine) RunkkColors.LimeFill else RunkkColors.Surface
    val content = if (isMine) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
    Row(
        modifier = Modifier
            .requiredWidthIn(max = TAG_MAX_WIDTH)
            .clip(RunkkShapes.Pill)
            .background(background)
            .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (face != null) Box(Modifier.size(TAG_FACE)) { face() } else Box(Modifier.width(4.dp).height(TAG_FACE))
        Text(
            name,
            style = RunkkTextStyles.text(11, FontWeight.Bold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (pace != null) Text(pace, style = RunkkTextStyles.text(11), color = content, maxLines = 1)
    }
}

/** 두 레인: 위는 상대, 아래는 나. 사이에 점선을 긋는다. */
@Composable
private fun Lanes(modifier: Modifier) {
    val lane = RunkkColors.Scrim
    val line = RunkkColors.Border
    Canvas(modifier.fillMaxWidth().height(RULER_HEIGHT + LANE_HEIGHT * 2)) {
        val laneHeight = LANE_HEIGHT.toPx()
        drawRect(lane.copy(alpha = LANE_ALPHA), size = size.copy(height = laneHeight * 2))
        drawLine(
            line,
            Offset(0f, laneHeight),
            Offset(size.width, laneHeight),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )
    }
}

/** 결승선: 두 레인을 가로지르는 점선 띠. */
@Composable
private fun FinishLine(modifier: Modifier) {
    val color = RunkkColors.TextPrimary
    Canvas(modifier.width(FINISH_WIDTH).height(LANE_HEIGHT * 2)) {
        drawLine(
            color,
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, size.height),
            strokeWidth = size.width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        )
    }
}

/** 가장자리 칩: 화면 밖 상대의 이름과 차이, 또는 앞뒤 인원. */
@Composable
private fun EdgeChip(text: String, modifier: Modifier) {
    Text(
        text,
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = CHIP_MAX_WIDTH)
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.Surface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 거리 자: 두 러너 사이를 선으로 잇고 가운데 알약에 차이를 적는다. 상대가 트랙에 없으면 선 없이 바탕만 둔다. */
@Composable
private fun Ruler(myX: Float, otherX: Float?, label: String?, modifier: Modifier) {
    val line = RunkkColors.TextSecondary
    BoxWithConstraints(modifier.fillMaxWidth().height(RULER_HEIGHT).background(RunkkColors.Surface)) {
        if (otherX == null) return@BoxWithConstraints
        val from = minOf(myX, otherX)
        val to = maxOf(myX, otherX)
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            drawLine(
                line,
                Offset(size.width * from, y),
                Offset(size.width * to, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            listOf(from, to).forEach { x ->
                drawLine(
                    line,
                    Offset(size.width * x, y - 5.dp.toPx()),
                    Offset(size.width * x, y + 5.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        if (label != null) {
            val middle = (from + to) / 2
            Text(
                label,
                style = RunkkTextStyles.text(11, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag(TRACK_RULER_TAG)
                    // 두 러너의 가운데에 알약 가운데가 오게 놓는다.
                    .layout { measurable, constraints ->
                        val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            pill.place(
                                (constraints.maxWidth * middle - pill.width / 2f).roundToInt(),
                                (constraints.maxHeight - pill.height) / 2
                            )
                        }
                    }
                    .clip(RunkkShapes.Pill)
                    .background(RunkkColors.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * [임시:NFR-성-7] 측정용 기록([임시:D-성-274]): 디버그 빌드에서만, 그린 거리값(상대 − 나, m)과 그린 시각(부팅 뒤 ms)을 남긴다. 수신 쪽
 * (data.live.ServerMessageDecoder)이 같은 태그로 남긴 시각과의 차를 실기기에서 잰다.
 */
@Composable
private fun LogDrawn(layout: TrackLayout.Result) {
    val context = LocalContext.current
    val isDebuggable = remember(context) { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    if (!isDebuggable) return
    val ahead = when (val other = layout.other) {
        is TrackLayout.Other.OnTrack -> ((other.x - layout.myX) * TrackLayout.WINDOW_METERS).toInt()
        is TrackLayout.Other.Edge -> if (other.isAhead) other.meters else -other.meters
        null -> return
    }
    SideEffect { Log.d(TRACK_TIMING_LOG_TAG, "draw ahead=$ahead at=${SystemClock.uptimeMillis()}") }
}

/** [임시:NFR-성-7] 측정 로그 태그(`adb logcat -s RunkkNfr7`). 수신 쪽(data.live)도 같은 글자를 쓴다. */
const val TRACK_TIMING_LOG_TAG = "RunkkNfr7"

/** 트랙 카드 안 자리의 시험 표지. */
const val TRACK_ME_TAG = "track-me"
const val TRACK_OTHER_TAG = "track-other"
const val TRACK_FINISH_TAG = "track-finish"
const val TRACK_AHEAD_TAG = "track-edge-ahead"
const val TRACK_BEHIND_TAG = "track-edge-behind"
const val TRACK_RULER_TAG = "track-ruler"

private const val OTHER_MOVE_MILLIS = 300
private const val LANE_ALPHA = 0.35f
private val RULER_HEIGHT = 28.dp
private val LANE_HEIGHT = 34.dp
private val MY_SIZE = 88.dp
private val OTHER_SIZE = 76.dp

/** 발을 레인 바닥에서 띄우는 높이. */
private val FOOT_LIFT = 10.dp
private val FINISH_WIDTH = 6.dp
private val TAG_FACE = 20.dp
private val TAG_MAX_WIDTH = 140.dp
private val CHIP_MAX_WIDTH = 150.dp
private val CHIP_BOTTOM = 34.dp
