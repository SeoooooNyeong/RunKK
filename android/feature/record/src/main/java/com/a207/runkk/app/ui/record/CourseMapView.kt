package com.a207.runkk.app.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.domain.record.RecordDetail
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 불안정 구간(시작 기준 경과 시간)을 경로 포인트 번호 구간으로 옮긴다. 경로 좌표에는 시각이 없어 전체 경과 시간 대비 비율로
 * 번호를 정한다(1초 표본이면 서버의 초 = 번호 근사와 같고, 걸러진 위치가 빠져 포인트가 적어도 경로 끝을 넘지 않는다).
 */
internal fun unstableIndexRanges(
    segments: List<RecordDetail.Unstable>,
    elapsed: Duration,
    pointCount: Int
): List<IntRange> {
    if (pointCount < 2 || elapsed.isZero || elapsed.isNegative) return emptyList()
    val last = pointCount - 1
    val total = elapsed.toMillis().toDouble()
    fun index(at: Duration): Int = (at.toMillis() / total * last).roundToInt().coerceIn(0, last)
    return segments.map { index(it.from)..index(it.to) }.filter { !it.isEmpty() }
}

/** 코스 지도 읽기 문구: 불안정 구간이 있으면 몇 곳인지 덧붙인다. */
internal fun courseMapDescription(pointCount: Int, unstableCount: Int): String = when {
    pointCount < 2 -> "코스 기록 없음"
    unstableCount > 0 -> "코스 지도, 신호 불안정 구간 ${unstableCount}곳 점선 표시"
    else -> "코스 지도"
}

/**
 * 기록 상세의 코스 지도(6.1.14 CourseMapView(points, unstable)). 쓰는 곳이 기록 상세 하나라 ui.record에 둔다(design-review.md M41).
 * 경로선은 라임, 불안정 구간([unstable]: points 인덱스 범위, FR-033, [unstableIndexRanges]로 만든다)은 라벤더 점선,
 * 시작은 흰 점·끝은 라임 점. 지도 타일은 외부 지도 SDK(C-005, Kakao) 연동 뒤 이 경로선 아래에 깐다. 지금은 경로선만 그린다.
 */
@Composable
fun CourseMapView(points: List<GeoPoint>, unstable: List<IntRange>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = courseMapDescription(points.size, unstable.size) }) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val span = maxOf(maxLat - minLat, maxLon - minLon).takeIf { it > 0 } ?: 1.0
        val pad = 24.dp.toPx()
        val scale = minOf(size.width - 2 * pad, size.height - 2 * pad) / span
        val ox = (size.width - (maxLon - minLon) * scale) / 2
        val oy = (size.height - (maxLat - minLat) * scale) / 2
        fun at(p: GeoPoint) = Offset(
            (ox + (p.longitude - minLon) * scale).toFloat(),
            (
                oy +
                    (maxLat - p.latitude) * scale
                ).toFloat()
        )
        val unstableIndexes = unstable.flatMap { it.toList() }.toSet()
        val stroke = 4.dp.toPx()
        for (i in 1 until points.size) {
            val segment = Path().apply {
                at(points[i - 1]).let { moveTo(it.x, it.y) }
                at(points[i]).let { lineTo(it.x, it.y) }
            }
            val isUnstable = i in unstableIndexes
            drawPath(
                segment,
                color = if (isUnstable) RunkkColors.Lavender else RunkkColors.Lime,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (isUnstable) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 8.dp.toPx())
                        )
                    } else {
                        null
                    }
                )
            )
        }
        drawCircle(RunkkColors.TextPrimary, radius = 6.dp.toPx(), center = at(points.first()))
        drawCircle(RunkkColors.Lime, radius = 6.dp.toPx(), center = at(points.last()))
    }
}

@Preview(name = "코스 지도", widthDp = 358, heightDp = 200, backgroundColor = 0xFF18181B, showBackground = true)
@Composable
private fun CourseMapViewPreview() {
    RunkkTheme(reduceMotion = true) {
        CourseMapView(
            points = listOf(
                GeoPoint(37.50, 127.00),
                GeoPoint(37.506, 127.008),
                GeoPoint(37.502, 127.016),
                GeoPoint(37.51, 127.024),
                GeoPoint(37.512, 127.03)
            ),
            unstable = listOf(2..3)
        )
    }
}
