package com.a207.runkk.app.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.splitPace
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.domain.record.RunCard
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 기록 카드 뒷면(K-1, FR-068): 앞면과 같은 틀([RecordCardShell])에 코스 칸과 거리·운동 시간·평균 페이스 세 칸(사용자 결정 2026-11-09).
 * 코스 칸은 지도 타일 없이 연한 격자 위에 보라 경로선, 출발은 흰 점·도착은 라임 점이다. 서버가 경로를 주지 않은 기록(실내 러닝 등)은
 * 칸 가운데에 "코스 기록 없음"을 쓴다. 색은 테마 변수라 다크에서도 그대로 바뀐다.
 */
@Composable
fun RecordCardBack(card: RunCard, modifier: Modifier = Modifier) {
    RecordCardShell(card, modifier) {
        CourseBox(card.route, Modifier.fillMaxWidth().weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardStat(
                "${RunkkFormat.km(card.distance)} ${stringResource(R.string.record_card_km)}",
                stringResource(R.string.record_card_stat_distance),
                Modifier.weight(1f),
                spoken = stringResource(R.string.record_distance_spoken, RunkkFormat.km(card.distance))
            )
            CardStat(
                RunkkFormat.duration(card.elapsed),
                stringResource(R.string.record_card_stat_time),
                Modifier.weight(1f),
                spoken = RunkkFormat.durationSpoken(card.elapsed)
            )
            CardStat(
                card.averagePace?.let(::splitPace) ?: RunkkFormat.EMPTY,
                stringResource(R.string.record_card_stat_pace),
                Modifier.weight(1f),
                spoken = RunkkFormat.paceSpoken(card.averagePace)
            )
        }
    }
}

/** 코스 칸(피그마 K-1 "코스 칸"): 둥근 연한 네모, 격자, 경로선과 출발·도착 점. 경로가 2점 미만이면 "코스 기록 없음". */
@Composable
private fun CourseBox(route: List<GeoPoint>, modifier: Modifier) {
    val gridColor = RunkkColors.RunimalTileBorder.copy(alpha = GRID_ALPHA)
    val lineColor = RunkkColors.Lavender
    val dotStroke = RunkkColors.Ink
    val endColor = RunkkColors.LimeFill
    Box(
        modifier = modifier
            .clip(COURSE_SHAPE)
            .background(RunkkColors.RunimalTile)
            .testTag(RECORD_CARD_COURSE_TAG)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        val hasCourse = route.size >= 2
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(gridColor)
            if (!hasCourse) return@Canvas
            val points = fitRoute(route, size.width, size.height, ROUTE_PAD.toPx())
            val path = Path().apply {
                points.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(
                path,
                color = lineColor,
                style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawEndpoint(points.first(), Color.White, dotStroke)
            drawEndpoint(points.last(), endColor, dotStroke)
        }
        if (!hasCourse) {
            Text(
                stringResource(R.string.record_course_none),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

private fun DrawScope.drawGrid(color: Color) {
    val step = GRID_STEP.toPx()
    val stroke = 1.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        y += step
    }
}

/** 출발·도착 점(지름 14, 테두리 2.5, 피그마 K-1). */
private fun DrawScope.drawEndpoint(at: Offset, fill: Color, stroke: Color) {
    val radius = DOT_RADIUS.toPx()
    drawCircle(fill, radius = radius, center = at)
    drawCircle(stroke, radius = radius, center = at, style = Stroke(width = DOT_STROKE.toPx()))
}

/**
 * 경로 좌표를 [width]×[height] 칸 안 화면 좌표로 옮긴다. 경도는 가운데 위도의 cos로 줄여 동서·남북 축척을 같게 하고(RouteCanvas와 같은
 * 등거리 투영), 가로세로 비율을 지켜 [pad]를 뺀 영역 가운데에 놓는다. 북쪽이 위다. 범위가 0인 축은 가운데에 둔다.
 */
internal fun fitRoute(route: List<GeoPoint>, width: Float, height: Float, pad: Float): List<Offset> {
    if (route.isEmpty()) return emptyList()
    val minLat = route.minOf { it.latitude }
    val maxLat = route.maxOf { it.latitude }
    val minLon = route.minOf { it.longitude }
    val maxLon = route.maxOf { it.longitude }
    val cosLat = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(MIN_COS)
    val spanX = (maxLon - minLon) * cosLat
    val spanY = maxLat - minLat
    val usableW = max(width - 2 * pad, 1f)
    val usableH = max(height - 2 * pad, 1f)
    val scale = when {
        spanX <= 0.0 && spanY <= 0.0 -> 0.0
        spanX <= 0.0 -> usableH / spanY
        spanY <= 0.0 -> usableW / spanX
        else -> min(usableW / spanX, usableH / spanY)
    }
    val left = (width - spanX * scale) / 2
    val top = (height - spanY * scale) / 2
    return route.map {
        Offset(
            (left + (it.longitude - minLon) * cosLat * scale).toFloat(),
            (top + (maxLat - it.latitude) * scale).toFloat()
        )
    }
}

/** 기록 카드 뒷면 코스 칸의 시험 표지. */
internal const val RECORD_CARD_COURSE_TAG = "record-card-course"

private val COURSE_SHAPE = RoundedCornerShape(24.dp)
private val GRID_STEP = 30.dp
private val ROUTE_PAD = 28.dp
private val LINE_WIDTH = 5.dp
private val DOT_RADIUS = 5.75.dp
private val DOT_STROKE = 2.5.dp
private const val GRID_ALPHA = 0.6f
private const val MIN_COS = 0.01
