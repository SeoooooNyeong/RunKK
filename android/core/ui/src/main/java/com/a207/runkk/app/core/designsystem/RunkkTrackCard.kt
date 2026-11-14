package com.a207.runkk.app.core.designsystem

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

/**
 * 트랙 보기 카드([임시:D-성-256], [임시:D-성-313], [임시:FR-성-27], 시안 350×328 모서리 26). 아래에서부터 거리 자 26, 레인 138을
 * 놓고 남은 하늘 칸에 [backdrop](내가 고른 아바타 무대 배경)을 깐다. 가로 위치는 [layout]이 정한다: 내 아바타([myAvatar])는 폭의 40%, 상대
 * ([otherAvatar])는 거리 차만큼 앞뒤, 화면 밖이면 그리지 않는다. 결승선은 [layout]에 있을 때만 그린다([임시:D-성-306]). 상대 위치는 300 ms
 * 안에 따라간다([임시:D-성-273], 움직임 줄이기면 바로 옮긴다).
 *
 * 이름표는 아바타 머리 위에 [myTag](라임 바탕), [otherTag](표면색 바탕, null이면 없음)와 현재 페이스([myPace], [otherPace]), 얼굴 원
 * ([myFace], [otherFace], 22dp 칸)을 적는다. 가장자리 칩 [aheadChip](오른쪽), [behindChip](왼쪽)과 거리 자 가운데 알약 [rulerLabel]은
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
    isMoving: Boolean = false,
    speedMetersPerSecond: Float = 0f,
    overlayBottom: Dp = 91.dp,
    backdrop: @Composable () -> Unit = {}
) {
    LogDrawn(layout)
    val onTrack = layout.other as? TrackLayout.Other.OnTrack
    val reduceMotion = LocalReduceMotion.current
    val travel = rememberTrackTravel(isMoving, speedMetersPerSecond, reduceMotion)
    val isDark = LocalRunkkColors.current.isDark
    val shape = RoundedCornerShape(26.dp)
    // 상대가 트랙에 들어올 때는 내 자리에서 미끄러져 오지 않게 바로 놓고, 트랙 위에서 움직일 때만 따라간다.
    val otherX by animateFloatAsState(
        targetValue = onTrack?.x ?: layout.myX,
        animationSpec = if (reduceMotion || onTrack == null) snap() else tween(OTHER_MOVE_MILLIS),
        label = "trackOtherX"
    )
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, if (isDark) RunkkColors.Border else TRACK_BORDER, shape)
    ) {
        val width = maxWidth
        val scene = TrackScene.dimensions(maxHeight.value, overlayBottom.value)
        Box(
            Modifier.fillMaxWidth().height(scene.skyHeight.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFCFE6FB), Color(0xFFEAF5FF))))
                .clipToBounds().clearAndSetSemantics {}
        ) {
            backdrop()
            if (isDark) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Lanes(isDark, { travel.floatValue }, Modifier.align(Alignment.BottomCenter).padding(bottom = RULER_HEIGHT))
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
                    .offset(x = width * otherX - scene.otherSize.dp / 2, y = -(maxHeight - scene.otherFootY.dp))
                    .testTag(TRACK_OTHER_TAG),
                size = scene.otherSize.dp,
                scale = scene.runnerScale,
                isMoving = false,
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
                .offset(x = width * layout.myX - scene.mySize.dp / 2, y = -(maxHeight - scene.myFootY.dp))
                .testTag(TRACK_ME_TAG),
            size = scene.mySize.dp,
            scale = scene.runnerScale,
            isMoving = isMoving && !reduceMotion,
            avatar = myAvatar
        )
        behindChip?.let {
            EdgeChip(
                it,
                Modifier.align(
                    Alignment.BottomStart
                ).padding(start = 8.dp, bottom = 46.dp).testTag(TRACK_BEHIND_TAG)
            )
        }
        aheadChip?.let {
            EdgeChip(
                it,
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 112.dp).testTag(TRACK_AHEAD_TAG)
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
    scale: Float,
    isMoving: Boolean,
    avatar: @Composable () -> Unit
) {
    Column(modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        if (tag != null) NameTag(tag, pace, isMine, face, scale)
        Box(
            Modifier.size(size).drawBehind {
                val shadowWidth = (if (isMine) 61.dp else 51.dp).toPx() * scale
                val shadowHeight = (if (isMine) 11.dp else 9.dp).toPx() * scale
                drawOval(
                    Color.Black.copy(alpha = 0.22f),
                    topLeft = Offset((this.size.width - shadowWidth) / 2, this.size.height - shadowHeight / 2),
                    size = Size(shadowWidth, shadowHeight)
                )
                if (isMoving) {
                    listOf(20f, 15f, 11f).forEachIndexed { index, length ->
                        drawRoundRect(
                            Color.White,
                            topLeft = Offset(
                                (-24f + index * 3f).dp.toPx() * scale,
                                (40f + index * 14f).dp.toPx() * scale
                            ),
                            size = Size(length.dp.toPx() * scale, 3.dp.toPx() * scale),
                            cornerRadius = CornerRadius(2.dp.toPx() * scale)
                        )
                    }
                }
            }
        ) { avatar() }
    }
}

/** 이름표: 높이 28, 얼굴 원 22, 이름 12, 페이스 11. 작은 카드에서는 주자와 같은 비율로 줄인다. */
@Composable
private fun NameTag(name: String, pace: String?, isMine: Boolean, face: (@Composable () -> Unit)?, scale: Float) {
    val background = if (isMine) RunkkColors.LimeFill else RunkkColors.Surface
    val content = if (isMine) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
    Row(
        modifier = Modifier
            .requiredWidthIn(max = TAG_MAX_WIDTH * scale)
            .height(28.dp * scale)
            .clip(RunkkShapes.Pill)
            .background(background)
            .padding(start = 3.dp * scale, end = 8.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(4.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (face != null) {
            Box(
                Modifier.size(TAG_FACE * scale).clip(CircleShape)
                    .border(2.dp * scale, if (isMine) RunkkColors.OnLimeFill else TRACK_PURPLE, CircleShape)
            ) { face() }
        }
        Text(
            name,
            style = RunkkTextStyles.text(12, FontWeight.Bold).copy(fontSize = (12 * scale).sp),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (pace != null) {
            Text(
                pace,
                style = RunkkTextStyles.text(11).copy(fontSize = (11 * scale).sp),
                color = if (isMine) TRACK_TAG_SECONDARY else RunkkColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

/** 두 레인과 잔디. 이동 상태는 그리기 단계에서만 읽어 매 프레임 카드 전체를 다시 구성하지 않는다. */
@Composable
private fun Lanes(isDark: Boolean, travel: () -> Float, modifier: Modifier) {
    Canvas(modifier.fillMaxWidth().height(TrackScene.LANE_HEIGHT.dp).clearAndSetSemantics {}) {
        drawRect(Color(0xFFE8805C))
        drawRect(Color(0xFFA9D58A), size = Size(size.width, 13.dp.toPx()))
        drawRect(
            Color(0xFFDE7450).copy(alpha = 0.55f),
            topLeft = Offset(0f, 79.dp.toPx()),
            size = Size(size.width, 59.dp.toPx())
        )
        drawLine(Color.White, Offset(0f, 14.dp.toPx()), Offset(size.width, 14.dp.toPx()), 2.dp.toPx())
        drawLine(
            Color.White.copy(alpha = 0.9f),
            Offset(0f, 70.dp.toPx()),
            Offset(size.width, 70.dp.toPx()),
            2.dp.toPx()
        )
        val offset = TrackScene.dashOffset(travel(), size.width / density).dp.toPx()
        listOf(42f to 0f, 105f to -13f).forEach { (y, stagger) ->
            var x = offset + stagger.dp.toPx()
            while (x < size.width) {
                drawLine(
                    Color.White.copy(alpha = 0.45f),
                    Offset(x, y.dp.toPx()),
                    Offset(x + 22.dp.toPx(), y.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                x += TrackScene.DASH_PERIOD.dp.toPx()
            }
        }
        if (isDark) drawRect(Color.Black.copy(alpha = 0.35f))
    }
}

/** 정지하거나 움직임 줄이기가 켜지면 현재 점선 위치를 보존한다. */
@Composable
private fun rememberTrackTravel(isMoving: Boolean, speed: Float, reduceMotion: Boolean): FloatState {
    val travel = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isMoving, speed, reduceMotion) {
        if (!isMoving || reduceMotion || !speed.isFinite() || speed <= 0f) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                travel.floatValue = TrackScene.advanceTravel(travel.floatValue, seconds, speed, isMoving, reduceMotion)
                previous = now
            }
        }
    }
    return travel
}

/** 결승선: 두 레인을 가로지르는 점선 띠. */
@Composable
private fun FinishLine(modifier: Modifier) {
    val color = RunkkColors.TextPrimary
    Canvas(modifier.width(FINISH_WIDTH).height(TrackScene.LANE_HEIGHT.dp).clearAndSetSemantics {}) {
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
    val line = TRACK_GREEN
    val tick = if (LocalRunkkColors.current.isDark) RunkkColors.Border else TRACK_BORDER
    Box(modifier.fillMaxWidth().height(RULER_HEIGHT).background(RunkkColors.Surface)) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawLine(tick, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            var x = 9.dp.toPx()
            var index = 0
            while (x < size.width) {
                drawLine(tick, Offset(x, 0f), Offset(x, (if (index % 5 == 4) 9.dp else 4.dp).toPx()), 1.dp.toPx())
                x += 20.7.dp.toPx()
                index++
            }
        }
        if (otherX == null) return@Box
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
                    Offset(size.width * x, y - 5.5.dp.toPx()),
                    Offset(size.width * x, y + 5.5.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        if (label != null) {
            val middle = (from + to) / 2
            Text(
                label,
                style = RunkkTextStyles.text(11, FontWeight.Bold, lineHeight = 17),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
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
                    .widthIn(min = 45.dp)
                    .height(17.dp)
                    .clip(RunkkShapes.Pill)
                    .background(TRACK_GREEN)
                    .padding(horizontal = 8.dp)
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
private val RULER_HEIGHT = TrackScene.RULER_HEIGHT.dp
private val FINISH_WIDTH = 6.dp
private val TAG_FACE = 22.dp
private val TAG_MAX_WIDTH = 140.dp
private val CHIP_MAX_WIDTH = 150.dp
private val TRACK_BORDER = Color(0xFFE9EDE0)
private val TRACK_GREEN = Color(0xFF3F6212)
private val TRACK_PURPLE = Color(0xFF5C4BDB)
private val TRACK_TAG_SECONDARY = Color(0xFF67705B)
