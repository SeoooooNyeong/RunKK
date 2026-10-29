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
 * 경로선 위에 얹는 마커 하나(D-261, D-262): 지점, 이름표(나 "나", 상대 표시명, 셀프런 "10월 2일의 나", 셀럽 이름, 없으면 그리지 않음),
 * 투명도(과거의 나 0.5, D-254), 마커 그림. 그림은 기본이 기쁜 기본 아바타이고 셀럽은 부르는 쪽이 공통 SD 그림을 넘긴다.
 */
@Immutable
data class RouteMarker(
    val point: GeoPoint,
    val label: String? = null,
    val alpha: Float = 1f,
    val tag: String = MY_MARKER_TAG,
    val figure: @Composable () -> Unit = {
        RunkkAvatar(faceKey = null, expression = AvatarExpression.JOY, modifier = Modifier.fillMaxSize())
    }
)

/** 내 마커의 시험 표지. 상대 마커는 [OPPONENT_MARKER_TAG]를 쓴다. */
const val MY_MARKER_TAG = "route-marker-mine"

/** 상대 마커의 시험 표지(ui.battle이 RouteMarker(tag = OPPONENT_MARKER_TAG)로 넘긴다). */
const val OPPONENT_MARKER_TAG = "route-marker-opponent"

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
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val projection = remember(route, markers.map { it.point }, width, height, padPx) {
            RouteProjection.fit(route.points + markers.map { it.point }, width, height, padPx)
        }
        Canvas(Modifier.fillMaxSize()) {
            val points = route.points
            if (points.size >= 2) {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val o = projection.project(p)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
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

/**
 * 위도·경도 → 화면 좌표(등거리 투영). 경도는 가운데 위도의 cos로 줄여 동서·남북 축척을 같게 하고, 범위를 [padding]을 뺀 영역에
 * 가로세로 비율을 지켜 넣는다. 점이 없거나 범위가 [MIN_SPAN_METERS]보다 좁으면 그 범위로 넓혀 가운데에 놓는다.
 */
internal class RouteProjection private constructor(
    private val centerLat: Double,
    private val centerLon: Double,
    private val cosLat: Double,
    private val scale: Double,
    private val originX: Float,
    private val originY: Float
) {
    fun project(p: GeoPoint): Offset = Offset(
        (originX + (p.longitude - centerLon) * cosLat * scale).toFloat(),
        (originY - (p.latitude - centerLat) * scale).toFloat()
    )

    companion object {
        /** 경로가 짧을 때 화면에 담는 최소 범위(m). 처음 몇 m가 화면 전체로 퍼지지 않게 한다. */
        const val MIN_SPAN_METERS = 120.0
        private const val METERS_PER_DEGREE = 111_320.0

        fun fit(points: List<GeoPoint>, width: Float, height: Float, padding: Float): RouteProjection {
            val minLat = points.minOfOrNull { it.latitude } ?: 0.0
            val maxLat = points.maxOfOrNull { it.latitude } ?: 0.0
            val minLon = points.minOfOrNull { it.longitude } ?: 0.0
            val maxLon = points.maxOfOrNull { it.longitude } ?: 0.0
            val centerLat = (minLat + maxLat) / 2
            val centerLon = (minLon + maxLon) / 2
            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(MIN_COS)
            val minSpan = MIN_SPAN_METERS / METERS_PER_DEGREE
            val spanX = max((maxLon - minLon) * cosLat, minSpan)
            val spanY = max(maxLat - minLat, minSpan)
            val usableW = max(width - 2 * padding, 1f)
            val usableH = max(height - 2 * padding, 1f)
            val scale = min(usableW / spanX, usableH / spanY)
            return RouteProjection(centerLat, centerLon, cosLat, scale, width / 2, height / 2)
        }

        private const val MIN_COS = 0.01
    }
}

/** RouteCanvas 시험 표지. */
const val ROUTE_CANVAS_TAG = "route-canvas"
