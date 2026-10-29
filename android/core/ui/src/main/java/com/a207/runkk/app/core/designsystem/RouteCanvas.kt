package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 측정 중 경로선(D-261, D60): 지도 타일 없이 경로 범위를 화면에 맞춰 등거리 투영(경도에 위도 cos 보정)으로 선을 그리고 마커를 얹는다.
 * 자유런·대결·일시정지 화면이 모두 이것을 쓴다. 지도 SDK는 부르지 않는다. 경로가 짧으면 [MIN_SPAN_METERS] 범위로 보아 처음부터
 * 과하게 확대되지 않게 한다. 마커는 그림 아래 가운데가 그 지점에 오게 놓는다. 장식 그림이라 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RouteCanvas(route: RouteLine, markers: List<RouteMarker>, modifier: Modifier = Modifier, markerSize: Dp = 72.dp) {
    val lineColor = RunkkColors.Lime
    val startColor = RunkkColors.Surface
    val startBorder = RunkkColors.Lime
    BoxWithConstraints(modifier = modifier.testTag(ROUTE_CANVAS_TAG)) {
        val density = LocalDensity.current
        val padPx = with(density) { (markerSize / 2).toPx() }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        // 투영은 경로 범위(덧붙일 때 계산해 둔 값)와 마커 지점만 보고, 범위가 그대로면 다시 계산하지 않는다.
        val bounds = route.bounds()
        val markerPoints = markers.map { it.point }
        val projection = remember(bounds, markerPoints, width, height, padPx) {
            val corners = bounds?.let {
                listOf(GeoPoint(it.minLatitude, it.minLongitude), GeoPoint(it.maxLatitude, it.maxLongitude))
            }.orEmpty()
            RouteProjection.fit(corners + markerPoints, width, height, padPx)
        }
        val path = remember(route, projection) {
            Path().apply {
                route.points.forEachIndexed { i, p ->
                    val o = projection.project(p)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val points = route.points
            if (points.size >= 2) {
                drawPath(
                    path,
                    color = lineColor,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            points.firstOrNull()?.let {
                val o = projection.project(it)
                drawCircle(startColor, radius = 7.dp.toPx(), center = o)
                drawCircle(startBorder, radius = 7.dp.toPx(), center = o, style = Stroke(width = 2.dp.toPx()))
            }
        }
        markers.forEach { marker -> MarkerAt(projection.project(marker.point), marker, markerSize) }
    }
}

/** 마커 하나: 그림 아래 가운데가 [at]에 오고 이름표는 그림 아래에 붙는다. */
@Composable
private fun MarkerAt(at: Offset, marker: RouteMarker, size: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag(marker.tag)
            .alpha(marker.alpha)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val figureHeight = size.roundToPx()
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        IntOffset((at.x - placeable.width / 2f).roundToInt(), (at.y - figureHeight).roundToInt())
                    )
                }
            }
    ) {
        Box(Modifier.size(size)) { marker.figure() }
        marker.label?.let {
            Text(
                it,
                style = RunkkTextStyles.text(11, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier
                    .background(RunkkColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/** RouteCanvas 시험 표지. */
const val ROUTE_CANVAS_TAG = "route-canvas"
