package com.a207.runkk.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import kotlin.math.roundToInt

/**
 * 첫 사용 가이드 막(J-1~J-8, D-277): 화면을 어둡게 덮고 지금 단계의 앵커([anchor], 루트 좌표)만 뚫어 강조한 뒤, 앵커 아래(앵커가
 * 화면 아래쪽이면 위)에 말풍선을 띄운다. 말풍선은 문구, "n/전체", "건너뛰기"·"다음"이고 마지막 단계는 "시작하기"만이다.
 * 막은 아래 화면으로 가는 누름을 모두 받아 버린다(가이드 중에는 홈을 누를 수 없다). 앵커를 아직 모르면 가운데에 띄운다.
 */
@Composable
fun FirstRunGuideOverlay(
    step: GuideStep,
    index: Int,
    stepCount: Int,
    anchor: Rect?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLast = index >= stepCount - 1
    val highlight = RunkkColors.Lavender
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) { detectTapGestures { } }
                .clearAndSetSemantics { }
        ) {
            drawRect(Color.Black.copy(alpha = DIM_ALPHA))
            if (anchor != null) {
                val hole = anchor.inflate(HOLE_PADDING.toPx())
                val radius = CornerRadius(HOLE_CORNER.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(highlight, hole.topLeft, hole.size, radius, style = Stroke(width = 3.dp.toPx()))
            }
        }
        val density = LocalDensity.current
        var bubbleHeight by remember { mutableIntStateOf(0) }
        val screenHeight = with(density) { maxHeight.toPx() }
        val gap = with(density) { BUBBLE_GAP.toPx() }
        val top = when {
            anchor == null -> (screenHeight - bubbleHeight) / 2f
            anchor.center.y < screenHeight / 2f -> anchor.bottom + gap
            else -> anchor.top - gap - bubbleHeight
        }.coerceIn(0f, (screenHeight - bubbleHeight).coerceAtLeast(0f))
        GuideBubble(
            step = step,
            page = stringResource(R.string.home_guide_page, index + 1, stepCount),
            isLast = isLast,
            onNext = onNext,
            onSkip = onSkip,
            modifier = Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .padding(horizontal = 16.dp)
                .onSizeChanged { bubbleHeight = it.height }
        )
    }
}

/** 말풍선: 문구, 쪽수, 버튼 줄. 보조 기술에는 화면 조각 제목으로 문구를 알린다. */
@Composable
private fun GuideBubble(
    step: GuideStep,
    page: String,
    isLast: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = stringResource(step.textRes)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Surface)
            .semantics { paneTitle = text }
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text, style = RunkkTextStyles.text(16, FontWeight.Bold), color = RunkkColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(page, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.home_guide_skip),
                        style = RunkkTextStyles.BodyStrong,
                        color = RunkkColors.TextPrimary
                    )
                }
            }
            RunkkLimePillButton(
                stringResource(if (isLast) R.string.home_guide_start else R.string.home_guide_next),
                onClick = onNext
            )
        }
    }
}

/** 가이드 막의 시험 표지. */
internal const val GUIDE_OVERLAY_TAG = "first-run-guide"

private const val DIM_ALPHA = 0.6f
private val HOLE_PADDING = 4.dp
private val HOLE_CORNER = 20.dp
private val BUBBLE_GAP = 12.dp
