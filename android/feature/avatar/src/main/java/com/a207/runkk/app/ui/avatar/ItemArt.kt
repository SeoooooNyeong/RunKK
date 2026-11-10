package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/**
 * 아이템 그림 칸(피그마 H-4: surfaceHigh 바탕, 모서리 12, 가운데 66 아이콘). 그림 키([Shop.Item.imageKey])별 실제 그림이 들어오기 전까지
 * 부위 실루엣(티셔츠·바지·신발·모자)을 그린다. 고른 칸은 라임, 나머지는 보라(피그마 79115·a4ed0). 장식이라 읽지 않는다.
 */
@Composable
internal fun ItemArt(item: Shop.Item, isSelected: Boolean, modifier: Modifier = Modifier, iconSize: Dp = ICON) {
    val tint = if (isSelected) RunkkColors.LimeFill else RunkkColors.Violet
    Box(
        modifier.clip(RunkkShapes.Small).background(RunkkColors.SurfaceHigh).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(iconSize)) { drawSlot(item.slot, tint) }
    }
}

private fun DrawScope.drawSlot(slot: ItemSlot, color: Color) {
    val w = size.width
    val h = size.height
    when (slot) {
        ItemSlot.TOP -> drawPath(
            Path().apply {
                moveTo(w * 0.32f, h * 0.12f)
                lineTo(w * 0.1f, h * 0.26f)
                lineTo(w * 0.18f, h * 0.44f)
                lineTo(w * 0.28f, h * 0.38f)
                lineTo(w * 0.28f, h * 0.88f)
                lineTo(w * 0.72f, h * 0.88f)
                lineTo(w * 0.72f, h * 0.38f)
                lineTo(w * 0.82f, h * 0.44f)
                lineTo(w * 0.9f, h * 0.26f)
                lineTo(w * 0.68f, h * 0.12f)
                quadraticTo(w * 0.5f, h * 0.26f, w * 0.32f, h * 0.12f)
                close()
            },
            color
        )

        ItemSlot.BOTTOM -> drawPath(
            Path().apply {
                moveTo(w * 0.24f, h * 0.12f)
                lineTo(w * 0.76f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.88f)
                lineTo(w * 0.58f, h * 0.88f)
                lineTo(w * 0.5f, h * 0.4f)
                lineTo(w * 0.42f, h * 0.88f)
                lineTo(w * 0.18f, h * 0.88f)
                close()
            },
            color
        )

        ItemSlot.SHOES -> {
            drawRoundRect(
                color,
                topLeft = Offset(w * 0.1f, h * 0.5f),
                size = Size(w * 0.8f, h * 0.24f),
                cornerRadius = CornerRadius(h * 0.12f)
            )
            drawRoundRect(
                color,
                topLeft = Offset(w * 0.1f, h * 0.3f),
                size = Size(w * 0.4f, h * 0.3f),
                cornerRadius = CornerRadius(h * 0.1f)
            )
        }

        ItemSlot.HAT -> {
            drawArc(
                color,
                180f,
                180f,
                useCenter = true,
                topLeft = Offset(w * 0.2f, h * 0.25f),
                size = Size(
                    w * 0.6f,
                    h * 0.6f
                )
            )
            drawRoundRect(
                color,
                topLeft = Offset(w * 0.2f, h * 0.52f),
                size = Size(w * 0.72f, h * 0.1f),
                cornerRadius = CornerRadius(h * 0.05f)
            )
        }
    }
}

private val ICON = 66.dp
