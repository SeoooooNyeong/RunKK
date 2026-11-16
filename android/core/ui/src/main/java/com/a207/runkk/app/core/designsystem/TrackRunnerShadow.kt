package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 내 주자와 상대 주자의 발밑 그림자 크기. */
internal enum class TrackRunnerShadow(val width: Dp, val height: Dp) {
    MINE(61.dp, 11.dp),
    OTHER(51.dp, 9.dp)
}
