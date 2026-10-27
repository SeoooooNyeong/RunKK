package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** 통계 칸의 모양: 값 글자 모양·색(지정하지 않으면 지금 테마의 본문 글자색), [isCentered]면 결과 화면처럼 가운데 정렬(3열 타일). */
@Immutable
data class StatTileStyle(
    val valueStyle: TextStyle = RunkkTextStyles.DisplayMedium,
    val valueColor: Color = Color.Unspecified,
    val isCentered: Boolean = false
)
