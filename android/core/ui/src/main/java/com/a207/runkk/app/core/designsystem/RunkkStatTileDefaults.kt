package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 통계 칸([RunkkStatTile]) 모양 미리 묶음. */
object RunkkStatTileDefaults {
    /**
     * 테두리 칸(피그마 F-02·F-18 크루 통계, 마이·아바타 숫자 칸): 표면, divider 테두리, 모서리 24, 안쪽 14, 간격 6, 라벨 12/18 본문색,
     * 값 20/24 Bold 본문색. 마이 칸은 `Outlined.copy(isCentered = true, labelPosition = StatTileLabelPosition.Bottom, …)`로 바꾼다.
     */
    val Outlined: StatTileStyle
        @Composable @ReadOnlyComposable
        get() = StatTileStyle(
            valueStyle = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 24),
            valueColor = RunkkColors.TextPrimary,
            borderColor = RunkkColors.Border,
            shape = RunkkShapes.ExtraLarge,
            contentPadding = PaddingValues(14.dp),
            labelStyle = RunkkTextStyles.Caption,
            labelColor = RunkkColors.TextPrimary,
            background = RunkkColors.Surface,
            spacing = 6.dp
        )
}
