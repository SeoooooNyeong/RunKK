package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 다섯 축 오각형(G-13·G-20·G-21·G-31). [scores]에 점수가 있는 축만 점을 찍고, 이웃한 두 축이 모두 있을 때만 선을 잇는다.
 * 다섯 축이 모두 있을 때만 면을 채운다(점수가 없는 축을 0점으로 그리지 않는다). [centerText]가 있으면 가운데에 쓴다(분석 중).
 */
@Composable
internal fun RunBodyRadar(scores: Map<RunBodyAxis, Int?>, modifier: Modifier = Modifier, centerText: String? = null) {
    val grid = RunkkColors.Border
    val line = RunkkColors.Violet
    Box(
        modifier = modifier.fillMaxWidth().height(
            RADAR_HEIGHT
        ).clip(RunkkShapes.Large).background(RunkkColors.Background)
    ) {
        Canvas(Modifier.align(Alignment.Center).offset(y = 8.dp).size(RADAR_SIZE).clearAndSetSemantics {}) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            for (level in 1..LEVELS) {
                drawPath(polygon(center, radius * level / LEVELS), grid, style = Stroke(1.dp.toPx()))
            }
            RunBodyAxis.entries.forEach { drawLine(grid, center, vertex(center, radius, it.ordinal), 1.dp.toPx()) }
            val points = RunBodyAxis.entries.associateWith { axis ->
                scores[axis]?.let { vertex(center, radius * it.coerceIn(0, MAX_SCORE) / MAX_SCORE, axis.ordinal) }
            }
            if (points.values.all { it != null }) {
                val area = Path().apply {
                    points.values.filterNotNull().forEachIndexed { i, p ->
                        if (i ==
                            0
                        ) {
                            moveTo(p.x, p.y)
                        } else {
                            lineTo(p.x, p.y)
                        }
                    }
                    close()
                }
                drawPath(area, line.copy(alpha = AREA_ALPHA))
            }
            RunBodyAxis.entries.forEach { axis ->
                val from = points[axis]
                val to = points[RunBodyAxis.entries[(axis.ordinal + 1) % RunBodyAxis.entries.size]]
                if (from != null && to != null) drawLine(line, from, to, 2.dp.toPx())
            }
            points.values.filterNotNull().forEach { drawCircle(line, 3.dp.toPx(), it) }
        }
        centerText?.let {
            Text(
                it,
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center).offset(y = 8.dp)
            )
        }
        AxisLabel(RunBodyAxis.INTENSITY, scores, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
        AxisLabel(
            RunBodyAxis.ENDURANCE,
            scores,
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).offset(y = (-20).dp)
        )
        AxisLabel(
            RunBodyAxis.STABILITY,
            scores,
            Modifier.align(Alignment.BottomEnd).padding(end = 52.dp, bottom = 4.dp)
        )
        AxisLabel(RunBodyAxis.FORM, scores, Modifier.align(Alignment.BottomStart).padding(start = 68.dp, bottom = 4.dp))
        AxisLabel(
            RunBodyAxis.FITNESS,
            scores,
            Modifier.align(Alignment.CenterStart).padding(start = 12.dp).offset(y = (-20).dp)
        )
    }
}

@Composable
private fun AxisLabel(axis: RunBodyAxis, scores: Map<RunBodyAxis, Int?>, modifier: Modifier) {
    val name = stringResource(axis.labelRes())
    val score = scores[axis]
    val value = score?.toString() ?: stringResource(R.string.record_runbody_no_score)
    val spoken = stringResource(R.string.record_runbody_radar_spoken, name, value)
    val tone = if (score != null) RunkkColors.TextSecondary else RunkkColors.TextSecondary.copy(alpha = MISSING_ALPHA)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = RunkkTextStyles.Caption, color = tone, textAlign = TextAlign.Center)
        Text(
            value,
            style = RunkkTextStyles.text(14, FontWeight.Bold),
            color = if (score != null) RunkkColors.TextPrimary else tone,
            textAlign = TextAlign.Center
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
private val RADAR_SIZE = 150.dp
private const val LEVELS = 5
private const val MAX_SCORE = 100
private const val AREA_ALPHA = 0.16f
private const val MISSING_ALPHA = 0.5f
