package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.Color

/** 배지 한 개의 색·글자(TierBadge 안에서만 쓴다). [tier]는 엠블럼에 넘길 서버 티어 이름(배치 중은 null). */
internal class TierBadgeLook(
    val tier: String?,
    val label: String,
    val spoken: String,
    val content: Color,
    val container: Color,
    val border: Color
)
