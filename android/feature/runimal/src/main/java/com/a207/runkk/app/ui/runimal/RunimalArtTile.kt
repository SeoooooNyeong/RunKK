package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt

/**
 * 러니멀 그림을 연한 둥근 네모 위에 올린다(피그마 G-4·G-6·G-11, R2-03). 바탕과 테두리는 그림의 일부라 테마와 관계없이 피그마 값을 쓴다.
 * 모서리는 한 변의 40%다(피그마 120 칸에 48).
 */
@Composable
internal fun RunimalArtTile(
    art: RunimalArt,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(percent = TILE_CORNER_PERCENT)
    Image(
        painter = painterResource(art.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(RunimalTileFill)
            .border(1.dp, RunimalTileBorder, shape)
            .padding(size * TILE_PADDING_RATIO)
    )
}

/** 그림 바탕 채움(피그마 #E9EEDC). */
internal val RunimalTileFill = Color(0xFFE9EEDC)

/** 그림 바탕 테두리(피그마 #D3D9C6). */
internal val RunimalTileBorder = Color(0xFFD3D9C6)

private const val TILE_CORNER_PERCENT = 40
private const val TILE_PADDING_RATIO = 0.06f
