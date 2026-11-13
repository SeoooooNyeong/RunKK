package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 그림자 높이 토큰. 색은 [RunkkColors.Shadow]·[RunkkColors.ShadowSoft]를 함께 쓴다:
 * `Modifier.shadow(RunkkElevation.Medium, shape, ambientColor = RunkkColors.Shadow, spotColor = RunkkColors.Shadow)`.
 */
object RunkkElevation {
    /** 낮은 카드(대결 카드, 4). */
    val Low: Dp = 4.dp

    /** 하단 고정 바·결과 카드(6). [RunkkBottomActionBar] 기본값이다. */
    val Medium: Dp = 6.dp

    /** 떠 있는 패널·메뉴(8). */
    val High: Dp = 8.dp
}
