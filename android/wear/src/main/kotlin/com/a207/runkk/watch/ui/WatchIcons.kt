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

/** 일시정지 아이콘(세로 막대 둘). 글자 옆에 붙는 장식이라 접근성 설명은 버튼 글자가 맡는다. */
@Composable
internal fun PauseIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier = modifier.size(size)) {
        val bar = Size(width = this.size.width * 0.26f, height = this.size.height * 0.8f)
        val top = this.size.height * 0.1f
        val radius = CornerRadius(bar.width / 2)
        drawRoundRect(color, Offset(this.size.width * 0.14f, top), bar, radius)
        drawRoundRect(color, Offset(this.size.width * 0.6f, top), bar, radius)
    }
}

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

/** 소리 아이콘(스피커와 음파 둘). 근접 알림 표시에 쓴다. */
@Composable
internal fun SoundIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val speaker = Path().apply {
            moveTo(w * 0.08f, h * 0.36f)
            lineTo(w * 0.26f, h * 0.36f)
            lineTo(w * 0.48f, h * 0.16f)
            lineTo(w * 0.48f, h * 0.84f)
            lineTo(w * 0.26f, h * 0.64f)
            lineTo(w * 0.08f, h * 0.64f)
            close()
        }
        drawPath(speaker, color)
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawArc(color, -50f, 100f, false, Offset(w * 0.38f, h * 0.3f), Size(w * 0.36f, h * 0.4f), style = stroke)
        drawArc(color, -55f, 110f, false, Offset(w * 0.3f, h * 0.12f), Size(w * 0.62f, h * 0.76f), style = stroke)
    }
}
