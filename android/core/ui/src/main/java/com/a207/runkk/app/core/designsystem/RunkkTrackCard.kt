package com.a207.runkk.app.core.designsystem

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

/**
 * 트랙 보기 카드([임시:D-성-256], [임시:D-성-313], [임시:FR-성-27], 시안 350×328 모서리 26). 아래에서부터 거리 자 26, 레인 138을
 * 놓고 남은 하늘 칸에 [backdrop](내가 고른 아바타 무대 배경)을 깐다. 가로 위치는 [layout]이 정한다: 내 아바타([myAvatar])는 폭의 40%, 상대
 * ([otherAvatar])는 거리 차만큼 앞뒤다. 결승선은 [layout]에 있을 때만 그린다([임시:D-성-306]). 상대 위치는 [GapMotion]이 받은 값 사이를
 * 예측해 매 프레임 부드럽게 옮기고(S15P31A207-335, 움직임 줄이기면 바로 옮긴다), 트랙 밖이어도 자르지 않은 위치에 그려 오는 쪽 가장자리에서
 * 들어온다(카드가 잘라 보이지 않는다). 처음 받은 격차가 트랙 안(출발 때 내 자리 등)이면 출발 쪽(왼쪽) 트랙 밖에서 들어와 제자리로 간다.
 * 상대 칸이 몸을 준비하는 동안([LocalTrackAvatarLoading])과 처음 구성한 프레임에는 그림자·이름표·몸을 함께 숨긴다(S15P31A207-335 2차).
 * 내 칸도 같다: 카운트다운에서 달리기 화면으로 바뀌면 트랙의 내 칸은 새 플레이어라 첫 판까지 몸이 없으므로, 그동안 그림자·이름표도
 * 숨겨 몸 없는 그림자·이름표가 보이지 않게 한다(S15P31A207-335 3차).
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
    var isOtherLoading by remember { mutableStateOf(false) }
    var isMineLoading by remember { mutableStateOf(false) }
    // 아바타 칸은 준비 중임을 효과(effect)로 알리므로 처음 구성한 프레임에는 아직 모른다. 그 한 프레임도 숨겨 그림자가 몸보다 먼저 보이지 않게 한다.
    var isComposed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        isComposed = true
    }
    val isOtherReady = isComposed && !isOtherLoading
    val isMineShown = isComposed && !isMineLoading
    val isOtherShown = layout.otherRawX != null && isOtherReady
    // 보이는 상대 가로 위치. 배치 단계(offset 람다)와 그리기 단계에서만 읽어 매 프레임 카드를 다시 구성하지 않는다.
    val otherX = rememberOtherX(layout, reduceMotion, isOtherReady)
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
        // 상대는 트랙 밖일 때도 미리 그려 둔다(카드가 잘라 보이지 않는다). 가장자리를 넘어 들어오는 모습이 그대로 보인다.
        if (otherTag != null) {
            val otherSize = scene.otherSize.dp
            val otherFootY = -(maxHeight - scene.otherFootY.dp)
            Runner(
                geometry = TrackRunnerGeometry(otherSize, scene.runnerScale, TrackRunnerShadow.OTHER),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset {
                        IntOffset((width * otherX.floatValue - otherSize / 2).roundToPx(), otherFootY.roundToPx())
                    }
                    .then(
                        if (onTrack != null && isOtherShown) {
                            Modifier.testTag(TRACK_OTHER_TAG)
                        } else {
                            Modifier.clearAndSetSemantics {}
                        }
                    )
                    .alpha(if (isOtherShown) 1f else PRELOAD_ALPHA),
                isMoving = false,
                onLoading = { isOtherLoading = it },
                avatar = otherAvatar
            )
        }
        Runner(
            geometry = TrackRunnerGeometry(scene.mySize.dp, scene.runnerScale, TrackRunnerShadow.MINE),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = width * layout.myX - scene.mySize.dp / 2, y = -(maxHeight - scene.myFootY.dp))
                .testTag(TRACK_ME_TAG)
                .alpha(if (isMineShown) 1f else PRELOAD_ALPHA),
            isMoving = isMoving && !reduceMotion,
            onLoading = { isMineLoading = it },
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
            otherX = if (onTrack != null && isOtherShown) otherX::floatValue else null,
            label = rulerLabel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        // 두 캐릭터를 모두 그린 뒤 이름표 둘을 올린다. 상대 이름표는 위 레인의 머리 위 줄을 지킨다.
        if (isMineShown) {
            Box(
                Modifier.offset(x = width * layout.myX - scene.mySize.dp / 2, y = scene.myTagY.dp)
                    .size(scene.mySize.dp, scene.tagHeight.dp).testTag(TRACK_ME_NAME_TAG),
                contentAlignment = Alignment.Center
            ) { NameTag(TrackRunnerLabel(myTag, myPace, true), myFace, scene.runnerScale) }
        }
        if (onTrack != null && otherTag != null && isOtherShown) {
            val otherSize = scene.otherSize.dp
            val tagY = scene.otherTagY.dp
            Box(
                Modifier.offset { IntOffset((width * otherX.floatValue - otherSize / 2).roundToPx(), tagY.roundToPx()) }
                    .size(scene.otherSize.dp, scene.tagHeight.dp).testTag(TRACK_OTHER_NAME_TAG),
                contentAlignment = Alignment.Center
            ) { NameTag(TrackRunnerLabel(otherTag, otherPace, false), otherFace, scene.runnerScale) }
        }
    }
}

/** 준비 중인 주자(트랙 밖 상대, 몸 준비 전의 나)를 미리 그릴 때의 불투명도. 0이면 그리기를 건너뛰어 첫 판이 오지 않을 수 있다. */
private const val PRELOAD_ALPHA = 0.01f

/**
 * 트랙 주자 아바타 미리 데우기(S15P31A207-335). 출발 전(카운트다운·크루런 대기실)에 [runners]를 트랙 주자 칸 크기로 구성만 하고
 * 그리지는 않는다. 아바타 칸이 모습을 풀고 서기·달리기 텍스처를 합성해 공용 캐시에 넣으므로 출발 직후 트랙의 아바타가 비거나 깜빡이지 않고 바로 보인다.
 * 자리를 차지하지 않고(크기 0으로 배치) 보조 기술에도 보이지 않는다.
 */
@Composable
fun RunkkTrackAvatarWarmup(runners: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    if (runners.isEmpty()) return
    Box(
        modifier
            .layout { measurable, _ ->
                val size = WARMUP_SIZE.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                layout(0, 0) { placeable.place(0, 0) }
            }
            // 그리지 않는다. 모습 풀기·텍스처 합성·공용 캐시 넣기는 그리기와 상관없이 일어난다.
            .drawWithContent {}
            .testTag(TRACK_WARMUP_TAG)
            .clearAndSetSemantics {}
    ) {
        CompositionLocalProvider(LocalTrackAvatarViewport provides true) {
            runners.forEach { runner -> Box(Modifier.fillMaxSize()) { runner() } }
        }
    }
}

/** 미리 데우는 칸 크기(트랙 상대 주자 칸, 시안 88dp). */
private val WARMUP_SIZE = 88.dp

/**
 * 보이는 상대 가로 위치(폭 비율, 자르지 않음, S15P31A207-335). 거리 차가 바뀌면 [GapMotion]에 넣고 예측이 멈출 때까지 매 프레임 갱신한다.
 * 움직임 줄이기면 받은 위치로 바로 옮긴다. 카드 밖 아주 먼 자리는 [OTHER_X_LIMIT]로 묶는다(잘려 보이지 않는다).
 *
 * 등장(2차): 이 카드가 처음 받은 격차가 트랙 안쪽([ENTRY_X]..1)이면 상대를 출발 쪽 트랙 밖([ENTRY_X])에 두고, 몸이 준비되면([isReady])
 * 그때부터 제자리로 들어오게 한다. 처음 값이 이미 트랙 밖이면 그 자리에서 시작해 오는 쪽 가장자리로 들어온다. 격차를 잃었다가 다시 알게 된
 * 때는 그 자리에 바로 놓는다.
 */
@Composable
private fun rememberOtherX(layout: TrackLayout.Result, reduceMotion: Boolean, isReady: Boolean): FloatState {
    val raw = layout.otherRawX
    val myX = layout.myX
    val ahead = raw?.let { (it - myX).toDouble() * TrackLayout.WINDOW_METERS }
    val motion = remember { GapMotion() }
    val shown = remember { mutableFloatStateOf(ENTRY_X) }
    // 마지막으로 넣은 격차와 첫 값을 받았는지. 준비 여부만 바뀌어 효과가 다시 돌 때 같은 값을 두 번 넣지 않는다.
    val fed = remember { FedGap() }
    LaunchedEffect(ahead, reduceMotion, isReady) {
        if (ahead == null) {
            motion.reset()
            fed.ahead = null
            return@LaunchedEffect
        }
        if (reduceMotion) {
            motion.reset()
            fed.ahead = ahead
            fed.hasFirst = true
            shown.floatValue = xOf(raw)
            return@LaunchedEffect
        }
        val entersFromStart = !fed.hasFirst && raw in ENTRY_X..1f
        // 처음 값(또는 격차를 다시 알게 된 때)은 첫 프레임 전에 그 자리에 놓아 가운데에서 잠깐 보이지 않게 한다.
        if (!motion.hasValue && !entersFromStart) shown.floatValue = xOf(raw)
        val enterFrom = (ENTRY_X - myX).toDouble() * TrackLayout.WINDOW_METERS
        // 값 넣기·등장 시작·그리기를 한 프레임에서 한다. 값이 올 때마다 효과가 다시 돌아도 그리지 않고 건너뛰는 프레임이 없다.
        while (isActive) {
            val isSettled = withFrameNanos { now ->
                val seconds = now / NANOS_PER_SECOND
                if (fed.ahead != ahead) {
                    fed.ahead = ahead
                    fed.hasFirst = true
                    if (entersFromStart) motion.enter(ahead, enterFrom, seconds) else motion.receive(ahead, seconds)
                }
                if (isReady) motion.startEntry(seconds)
                shown.floatValue = xOf(myX + (motion.frame(seconds) / TrackLayout.WINDOW_METERS).toFloat())
                motion.isSettled(seconds)
            }
            if (isSettled) break
        }
    }
    return shown
}

private class FedGap {
    var ahead: Double? = null
    var hasFirst = false
}

/** 상대가 들어오기 시작하는 출발 쪽 트랙 밖 자리(폭 비율). 상대 칸(폭의 약 28%)이 카드 왼쪽 밖에 다 숨는다. */
private const val ENTRY_X = -0.2f

private fun xOf(raw: Float): Float = raw.coerceIn(-OTHER_X_LIMIT, 1f + OTHER_X_LIMIT)

private const val OTHER_X_LIMIT = 0.5f
private const val NANOS_PER_SECOND = 1_000_000_000.0

/** 달리는 사람 한 명. 발은 칸 바닥에, 이름표는 모든 캐릭터 위에 놓는다. */
@Composable
private fun Runner(
    geometry: TrackRunnerGeometry,
    isMoving: Boolean,
    onLoading: (Boolean) -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = geometry.scale
    Box(
        modifier.size(geometry.size).drawBehind {
            val shadowWidth = geometry.shadow.width.toPx() * scale
            val shadowHeight = geometry.shadow.height.toPx() * scale
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
    ) {
        CompositionLocalProvider(
            LocalTrackAvatarViewport provides true,
            LocalTrackAvatarLoading provides onLoading
        ) { avatar() }
    }
}

/** 이름표: 높이 28, 얼굴 원 22, 이름 12, 페이스 11. 작은 카드에서는 주자와 같은 비율로 줄인다. */
@Composable
private fun NameTag(label: TrackRunnerLabel, face: (@Composable () -> Unit)?, scale: Float) {
    val background = if (label.isMine) RunkkColors.LimeFill else RunkkColors.Surface
    val content = if (label.isMine) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
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
                    .border(2.dp * scale, if (label.isMine) RunkkColors.OnLimeFill else TRACK_PURPLE, CircleShape)
            ) { face() }
        }
        Text(
            label.name,
            style = RunkkTextStyles.text(12, FontWeight.Bold).copy(fontSize = (12 * scale).sp),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (label.pace != null) {
            Text(
                label.pace,
                style = RunkkTextStyles.text(11).copy(fontSize = (11 * scale).sp),
                color = if (label.isMine) TRACK_TAG_SECONDARY else RunkkColors.TextSecondary,
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
        // 밤 거르개는 바탕에만 적용한다. 레인 선은 그 위에 그려 밤 배경에서도 대비를 지킨다.
        if (isDark) drawRect(Color.Black.copy(alpha = 0.35f))
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
                    Color.White.copy(alpha = if (isDark) 0.72f else 0.45f),
                    Offset(x, y.dp.toPx()),
                    Offset(x + 22.dp.toPx(), y.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                x += TrackScene.DASH_PERIOD.dp.toPx()
            }
        }
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
private fun Ruler(myX: Float, otherX: (() -> Float)?, label: String?, modifier: Modifier) {
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
        // 상대 위치는 그리기·배치 단계에서만 읽는다(매 프레임 바뀐다).
        Canvas(Modifier.fillMaxSize()) {
            val from = minOf(myX, otherX())
            val to = maxOf(myX, otherX())
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
                        val middle = (myX + otherX()) / 2
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
const val TRACK_ME_NAME_TAG = "track-me-name"
const val TRACK_OTHER_NAME_TAG = "track-other-name"
const val TRACK_WARMUP_TAG = "track-avatar-warmup"

private val RULER_HEIGHT = TrackScene.RULER_HEIGHT.dp
private val FINISH_WIDTH = 6.dp
private val TAG_FACE = 22.dp
private val TAG_MAX_WIDTH = 140.dp
private val CHIP_MAX_WIDTH = 150.dp
private val TRACK_BORDER = Color(0xFFE9EDE0)
private val TRACK_GREEN = Color(0xFF3F6212)
private val TRACK_PURPLE = Color(0xFF5C4BDB)
private val TRACK_TAG_SECONDARY = Color(0xFF67705B)
