package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 알약 배지 크기(D-520, 7.4절): 치수만 담는다. 임시 SMALL은 테두리 1.5라 높이 31이다. */
enum class TierBadgeSize(val height: Dp, val emblem: TierEmblemSize, val textSize: TextUnit) {
    XSMALL(20.dp, TierEmblemSize.XSMALL, 11.sp),
    SMALL(30.dp, TierEmblemSize.SMALL, 12.sp)
}
