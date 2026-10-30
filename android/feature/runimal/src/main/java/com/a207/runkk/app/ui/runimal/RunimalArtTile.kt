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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors

/**
 * 러니멀 그림을 연한 둥근 네모 위에 올린다(피그마 G-4·G-6·G-11, R2-03). 바탕과 테두리는 [RunkkColors.RunimalTile]·
 * [RunkkColors.RunimalTileBorder]다(라이트는 피그마 연두 네모, 다크는 카드색 바탕에 옅은 테두리, D95). 모서리는 한 변의 40%다(피그마 120 칸에 48).
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
            .background(RunkkColors.RunimalTile)
            .border(1.dp, RunkkColors.RunimalTileBorder, shape)
            .padding(size * TILE_PADDING_RATIO)
    )
}

private const val TILE_CORNER_PERCENT = 40
private const val TILE_PADDING_RATIO = 0.06f
