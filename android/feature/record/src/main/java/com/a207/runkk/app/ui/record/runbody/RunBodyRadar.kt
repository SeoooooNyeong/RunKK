package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.ui.record.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 다섯 축 오각형(G-13·G-20·G-21·G-31, 피그마 310x258 상자 값). [scores]에 점수가 있는 축만 점(지름 4)을 찍고, 이웃한 두 축이 모두 있을 때만
 * 선을 잇는다. 면은 채우지 않는다(점수가 없는 축을 0점으로 그리지 않는다). 점수가 없는 축은 이름·값 묶음 전체를 흐리게(0.35) 한다.
 * [centerText]가 있으면 분석 중이다: 가운데에 쓰고(17 Bold), 축 이름은 그대로, 값 "—"만 흐리게(0.45) 한다.
 */
@Composable
internal fun RunBodyRadar(scores: Map<RunBodyAxis, Int?>, modifier: Modifier = Modifier, centerText: String? = null) {
    val grid = RunkkColors.Border
    val line = RunkkColors.Violet
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RADAR_HEIGHT)
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Background)
    ) {
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = RADAR_CENTER_Y - RADAR_RADIUS)
                .size(RADAR_RADIUS * 2)
                .clearAndSetSemantics {}
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            for (level in 1..LEVELS) {
                drawPath(polygon(center, radius * level / LEVELS), grid, style = Stroke(1.dp.toPx()))
            }
            RunBodyAxis.entries.forEach { drawLine(grid, center, vertex(center, radius, it.ordinal), 1.dp.toPx()) }
            val points = RunBodyAxis.entries.associateWith { axis ->
                scores[axis]?.let { vertex(center, radius * it.coerceIn(0, MAX_SCORE) / MAX_SCORE, axis.ordinal) }
            }
            RunBodyAxis.entries.forEach { axis ->
                val from = points[axis]
                val to = points[RunBodyAxis.entries[(axis.ordinal + 1) % RunBodyAxis.entries.size]]
                if (from != null && to != null) drawLine(line, from, to, 2.dp.toPx())
            }
            points.values.filterNotNull().forEach { drawCircle(line, POINT_RADIUS.toPx(), it) }
        }
        centerText?.let {
            Text(
                it,
                style = RunkkTextStyles.text(17, FontWeight.Bold, lineHeight = CENTER_LINE_HEIGHT),
                color = RunkkColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = RADAR_CENTER_Y - (CENTER_LINE_HEIGHT / 2).dp)
            )
        }
        AxisLabels(scores, isAnalyzing = centerText != null)
    }
}

/** 축 이름·점수 자리(피그마 좌표): 운동 강도 위 가운데, 지구력·심폐체력 양옆 위 75.5, 페이스 안정성·러닝 폼 아래 216. */
@Composable
private fun BoxScope.AxisLabels(scores: Map<RunBodyAxis, Int?>, isAnalyzing: Boolean) {
    AxisLabel(RunBodyAxis.INTENSITY, scores, isAnalyzing, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
    AxisLabel(
        RunBodyAxis.ENDURANCE,
        scores,
        isAnalyzing,
        Modifier.align(Alignment.TopEnd).padding(top = SIDE_LABEL_TOP).width(SIDE_LABEL_WIDTH)
    )
    AxisLabel(
        RunBodyAxis.FITNESS,
        scores,
        isAnalyzing,
        Modifier.align(Alignment.TopStart).padding(top = SIDE_LABEL_TOP).width(SIDE_LABEL_WIDTH)
    )
    AxisLabel(
        RunBodyAxis.STABILITY,
        scores,
        isAnalyzing,
        Modifier
            .align(Alignment.TopEnd)
            .padding(end = BOTTOM_LABEL_INSET, top = BOTTOM_LABEL_TOP)
            .width(BOTTOM_LABEL_WIDTH)
    )
    AxisLabel(
        RunBodyAxis.FORM,
        scores,
        isAnalyzing,
        Modifier
            .align(Alignment.TopStart)
            .padding(start = BOTTOM_LABEL_INSET, top = BOTTOM_LABEL_TOP)
            .width(BOTTOM_LABEL_WIDTH)
    )
}

@Composable
private fun AxisLabel(axis: RunBodyAxis, scores: Map<RunBodyAxis, Int?>, isAnalyzing: Boolean, modifier: Modifier) {
    val name = stringResource(axis.labelRes())
    val score = scores[axis]
    val value = score?.toString() ?: stringResource(R.string.record_runbody_no_score)
    val spoken = stringResource(R.string.record_runbody_radar_spoken, name, value)
    val groupAlpha = if (score == null && !isAnalyzing) MISSING_ALPHA else 1f
    Column(
        modifier = modifier.alpha(groupAlpha).semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name,
            style = RunkkTextStyles.text(12, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            value,
            style = RunkkTextStyles.text(14, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (score == null && isAnalyzing) MUTED_VALUE_ALPHA else 1f)
        )
    }
}

/** 위에서 시계 방향으로 [index]번째 꼭짓점. */
private fun vertex(center: Offset, radius: Float, index: Int): Offset {
    val angle = -PI / 2 + 2 * PI * index / RunBodyAxis.entries.size
    return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}

private fun polygon(center: Offset, radius: Float): Path = Path().apply {
    RunBodyAxis.entries.forEach { axis ->
        val p = vertex(center, radius, axis.ordinal)
        if (axis.ordinal == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private val RADAR_HEIGHT = 258.dp

/** 오각형 외접 반지름과 상자 위에서 중심까지(피그마 79.5, 123.2). */
private val RADAR_RADIUS: Dp = 79.5.dp
private val RADAR_CENTER_Y: Dp = 123.2.dp
private val POINT_RADIUS = 2.dp
private val SIDE_LABEL_TOP = 75.5.dp
private val SIDE_LABEL_WIDTH = 72.dp
private val BOTTOM_LABEL_TOP = 216.dp
private val BOTTOM_LABEL_WIDTH = 111.dp
private val BOTTOM_LABEL_INSET = 32.dp
private const val CENTER_LINE_HEIGHT = 24
private const val LEVELS = 5
private const val MAX_SCORE = 100

/** 점수가 없는 축 묶음의 불투명도(피그마 0.35). */
private const val MISSING_ALPHA = 0.35f
