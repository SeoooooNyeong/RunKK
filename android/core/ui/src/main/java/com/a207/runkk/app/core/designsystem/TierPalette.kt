package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 티어 한 개의 색(D-496, design-oo 05-21 7.3). [content]는 배지 글자·테두리, [container]는 확정 배지 바탕이고
 * emblem 세 값은 엠블럼 드로어블(tier_emblem_*)에 박힌 색과 같다.
 */
@Immutable
data class TierPalette(
    val content: Color,
    val container: Color,
    val emblemBase: Color,
    val emblemShade: Color,
    val emblemHighlight: Color
)
