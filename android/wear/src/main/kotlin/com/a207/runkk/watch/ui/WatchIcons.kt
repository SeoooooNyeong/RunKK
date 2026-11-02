package com.a207.runkk.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 재생(재개) 아이콘(오른쪽 삼각형). */
@Composable
internal fun PlayIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.1f)
            lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.9f)
            close()
        }
        drawPath(path, color)
    }
}

/** 메인 카드 아이콘(피그마 Q1~Q4): 자유런 바람, 배틀런 번개, 셀프런 되감는 시계, 셀럽런 별. 카드 글자가 뜻을 말하므로 장식이다. */
@Composable
internal fun ModeIcon(mode: WatchMode, color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        when (mode) {
            WatchMode.FREE -> {
                drawLine(color, Offset(w * 0.08f, h * 0.3f), Offset(w * 0.62f, h * 0.3f), stroke.width, StrokeCap.Round)
                drawLine(
                    color,
                    Offset(w * 0.08f, h * 0.52f),
                    Offset(w * 0.82f, h * 0.52f),
                    stroke.width,
                    StrokeCap.Round
                )
                drawLine(color, Offset(w * 0.2f, h * 0.74f), Offset(w * 0.6f, h * 0.74f), stroke.width, StrokeCap.Round)
                drawArc(
                    color,
                    -90f,
                    200f,
                    false,
                    Offset(w * 0.52f, h * 0.1f),
                    Size(w * 0.36f, h * 0.4f),
                    style = stroke
                )
            }

            WatchMode.BATTLE -> drawPath(
                Path().apply {
                    moveTo(w * 0.6f, 0f)
                    lineTo(w * 0.15f, h * 0.58f)
                    lineTo(w * 0.48f, h * 0.58f)
                    lineTo(w * 0.38f, h)
                    lineTo(w * 0.85f, h * 0.4f)
                    lineTo(w * 0.52f, h * 0.4f)
                    close()
                },
                color
            )

            WatchMode.SELF -> {
                drawArc(color, -60f, 300f, false, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.52f), stroke.width, StrokeCap.Round)
                drawLine(
                    color,
                    Offset(w * 0.5f, h * 0.52f),
                    Offset(w * 0.66f, h * 0.62f),
                    stroke.width,
                    StrokeCap.Round
                )
            }

            WatchMode.CELEB -> {
                val star = Path()
                for (i in 0 until STAR_POINTS * 2) {
                    val radius = if (i % 2 == 0) w * 0.5f else w * 0.21f
                    val angle = Math.toRadians(-90.0 + i * 180.0 / STAR_POINTS)
                    val x = w * 0.5f + (radius * kotlin.math.cos(angle)).toFloat()
                    val y = h * 0.52f + (radius * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, color)
            }
        }
    }
}

/** 정지(종료) 아이콘(둥근 네모). */
@Composable
internal fun StopIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier = modifier.size(size)) {
        drawRoundRect(color, cornerRadius = CornerRadius(this.size.width * 0.15f))
    }
}

/** 심박 하트. 옆 숫자를 함께 읽어 주므로 장식이다. */
@Composable
internal fun HeartIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val heart = Path().apply {
            moveTo(w * 0.5f, h * 0.92f)
            cubicTo(w * -0.1f, h * 0.5f, w * 0.12f, h * -0.05f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.88f, h * -0.05f, w * 1.1f, h * 0.5f, w * 0.5f, h * 0.92f)
            close()
        }
        drawPath(heart, color)
    }
}

/** 별 꼭짓점 수. */
private const val STAR_POINTS = 5
