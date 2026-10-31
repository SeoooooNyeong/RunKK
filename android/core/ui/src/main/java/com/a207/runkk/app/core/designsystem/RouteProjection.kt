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
