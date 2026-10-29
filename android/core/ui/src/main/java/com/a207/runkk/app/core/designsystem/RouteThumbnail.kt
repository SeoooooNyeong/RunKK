package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 기록 목록·달력 줄의 경로 모양 썸네일(D-279). [points]는 서버 routeShape를 그대로 옮긴 0~1 정규화 점이고 x는 동쪽, y는 북쪽으로
 * 커진다(서버 running.RouteShape). 화면은 y를 뒤집어 북쪽을 위로 그린다. 점이 2개 미만이면 아무것도 그리지 않는다.
 * 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RouteThumbnail(
    points: List<Offset>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = RunkkColors.TextPrimary,
    strokeWidth: Dp = 3.dp
) {
    Canvas(modifier.size(size)) {
        if (points.size < MIN_POINTS) return@Canvas
        val inset = strokeWidth.toPx()
        val width = this.size.width - inset * 2
        val height = this.size.height - inset * 2
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = inset + point.x.coerceIn(0f, 1f) * width
            val y = inset + (1f - point.y.coerceIn(0f, 1f)) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private const val MIN_POINTS = 2
