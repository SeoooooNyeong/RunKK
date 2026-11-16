package com.a207.runkk.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import kotlin.math.roundToInt

/**
 * 첫 사용 가이드 단계 막([임시:FR-성-35], [임시:D-성-310], D-277): 화면을 어둡게 덮고 지금 단계의 앵커([anchor], 루트 좌표)만 뚫어 흰
 * 테두리로 강조한 뒤, 앵커 바로 아래(앵커가 화면 아래쪽이면 위)에 꼬리 달린 좁은 말풍선(너비 300dp)을 띄운다(R2-16, D90).
 * 꼬리는 앵커 가운데를 가리킨다. 말풍선은 작은 그림, 제목, 쪽수, 설명, 단계 점, 「건너뛰기」·「이전」(2단계부터)·「다음」이고 마지막 단계는
 * 「시작하기」다. 막은 아래 화면으로 가는 누름을 모두 받아 버린다(가이드 중에는 홈을 누를 수 없다). 앵커를 아직 모르면 꼬리 없이 가운데에
 * 띄운다. 뒤로 가기와 「건너뛰기」는 [onBack]으로 가이드를 닫는다(앱을 닫지 않는다, AN8-15).
 */
@Composable
fun FirstRunGuideOverlay(
    step: GuideStep,
    index: Int,
    stepCount: Int,
    anchor: Rect?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    // 보조 기술이 아래 홈보다 말풍선을 먼저 읽게 한다(터치는 막이 받아 버린다).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(GUIDE_OVERLAY_TAG)
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
    ) {
        val density = LocalDensity.current
        val hole = anchor?.inflate(with(density) { HOLE_PADDING.toPx() })
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) { detectTapGestures { } }
                .clearAndSetSemantics { }
        ) {
            drawRect(Color.Black.copy(alpha = DIM_ALPHA))
            if (hole != null) {
                val radius = CornerRadius(HOLE_CORNER.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(Color.White, hole.topLeft, hole.size, radius, style = Stroke(width = RING_WIDTH.toPx()))
            }
        }
        var bubbleHeight by remember { mutableIntStateOf(0) }
        val place = with(density) {
            bubblePlacement(
                hole = hole,
                screen = Size(maxWidth.toPx(), maxHeight.toPx()),
                bubble = Size(
                    minOf(BUBBLE_WIDTH.toPx(), maxWidth.toPx() - 2 * SCREEN_MARGIN.toPx()),
                    bubbleHeight.toFloat()
                ),
                margin = SCREEN_MARGIN.toPx(),
                gap = BUBBLE_GAP.toPx(),
                tailInset = TAIL_INSET.toPx()
            )
        }
        GuideBubble(
            step = step,
            index = index,
            stepCount = stepCount,
            tail = place.tail,
            tailX = with(density) { place.tailX.toDp() },
            onNext = onNext,
            onPrevious = onPrevious,
            onSkip = onBack,
            modifier = Modifier
                .offset { IntOffset(place.left.roundToInt(), place.top.roundToInt()) }
                .width(with(density) { place.width.toDp() })
                .onSizeChanged { bubbleHeight = it.height }
        )
    }
}

/**
 * 말풍선([임시:D-성-310]): 꼬리, 작은 그림과 제목과 쪽수(「1 / 4」), 설명, 아래 줄(단계 점, 「건너뛰기」, 2단계부터 「이전」, 「다음」 또는
 * 마지막 단계의 「시작하기」). 마지막 단계는 「시작하기」가 닫으므로 「건너뛰기」를 두지 않는다. 보조 기술에는 화면 조각 제목으로 제목과
 * 설명을 알린다.
 */
@Composable
private fun GuideBubble(
    step: GuideStep,
    index: Int,
    stepCount: Int,
    tail: GuideTail,
    tailX: Dp,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(step.titleRes)
    val text = stringResource(step.textRes)
    val page = stringResource(R.string.home_guide_page, index + 1, stepCount)
    val isLast = index >= stepCount - 1
    val color = RunkkColors.Surface
    Column(modifier = modifier.testTag(GUIDE_BUBBLE_TAG)) {
        if (tail == GuideTail.UP) BubbleTail(isUp = true, x = tailX, color = color)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BUBBLE_CORNER))
                .background(color)
                .semantics { paneTitle = "$title, $text" }
                .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(STEP_ICON_BOX).clip(CircleShape).background(RunkkColors.LimeFill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        stepIcon(step),
                        contentDescription = null,
                        tint = RunkkColors.OnLimeFill,
                        modifier = Modifier.size(STEP_ICON)
                    )
                }
                Text(
                    title,
                    style = RunkkTextStyles.text(17, FontWeight.Bold, lineHeight = 24),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(page, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary)
            }
            Text(
                text,
                style = RunkkTextStyles.text(14, FontWeight.Medium, lineHeight = 22),
                color = RunkkColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RunkkPageDots(current = index, count = stepCount, description = page)
                Spacer(Modifier.weight(1f))
                if (!isLast) {
                    RunkkButton(
                        stringResource(R.string.home_guide_skip),
                        onClick = onSkip,
                        style = RunkkButtonStyle.Text,
                        size = RunkkButtonSize.Small
                    )
                }
                if (index > 0) {
                    RunkkButton(
                        stringResource(R.string.home_guide_previous),
                        onClick = onPrevious,
                        style = RunkkButtonStyle.Text,
                        size = RunkkButtonSize.Small
                    )
                }
                RunkkLimePillButton(
                    stringResource(if (isLast) R.string.home_guide_start else R.string.home_guide_next),
                    onClick = onNext
                )
            }
        }
        if (tail == GuideTail.DOWN) BubbleTail(isUp = false, x = tailX, color = color)
    }
}

/** 단계의 작은 그림(말풍선 제목 왼쪽, 장식). */
private fun stepIcon(step: GuideStep): ImageVector = when (step) {
    GuideStep.FREE_RUN -> RunkkIcons.DirectionsRun
    GuideStep.BATTLE_RUN -> RunkkIcons.Swords
    GuideStep.RECORD_RUN -> RunkkIcons.Walk
    GuideStep.MY_RUNNER -> RunkkIcons.User
}

/** 말풍선 꼬리(너비 18, 높이 9 삼각형). [x]는 말풍선 안에서 꼬리 가운데의 가로 위치다. */
@Composable
private fun BubbleTail(isUp: Boolean, x: Dp, color: Color) {
    Canvas(
        modifier = Modifier
            .testTag(GUIDE_TAIL_TAG)
            .offset(x = x - TAIL_WIDTH / 2)
            .size(TAIL_WIDTH, TAIL_HEIGHT)
    ) {
        val path = Path().apply {
            if (isUp) {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** 가이드 막의 시험 표지. */
internal const val GUIDE_OVERLAY_TAG = "first-run-guide"

/** 말풍선(꼬리 포함)과 꼬리의 시험 표지. */
internal const val GUIDE_BUBBLE_TAG = "first-run-guide-bubble"
internal const val GUIDE_TAIL_TAG = "first-run-guide-tail"

/** 말풍선 너비(J-1~J-7 Coachmark 300). */
internal val BUBBLE_WIDTH = 300.dp

private const val DIM_ALPHA = 0.6f
private val HOLE_PADDING = 6.dp
private val HOLE_CORNER = 30.dp
private val RING_WIDTH = 2.dp
private val BUBBLE_GAP = 6.dp
private val BUBBLE_CORNER = 20.dp
private val SCREEN_MARGIN = 16.dp
private val TAIL_WIDTH = 18.dp
private val TAIL_HEIGHT = 9.dp
private val STEP_ICON_BOX = 32.dp
private val STEP_ICON = 18.dp

/** 꼬리가 말풍선 둥근 모서리에 걸리지 않게 띄우는 최소 거리. */
private val TAIL_INSET = 28.dp
