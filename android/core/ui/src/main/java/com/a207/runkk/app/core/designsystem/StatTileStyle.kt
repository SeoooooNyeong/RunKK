package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * 통계 칸의 모양: 값 글자 모양·색(지정하지 않으면 지금 테마의 본문 글자색), [isCentered]면 결과 화면처럼 가운데 정렬(3열 타일).
 * 나머지는 지정하지 않으면(null·Unspecified) 지금 모양 그대로다: [borderColor] 테두리(없음), [shape] 모서리(가운데 16·기본 20),
 * [contentPadding] 안쪽(가운데 8×16·기본 16), [labelPosition] 라벨 자리(위), [labelStyle] 라벨 글자(가운데 11·기본 12),
 * [labelColor] 라벨 색(보조색), [background] 바탕(표면), [spacing] 줄 간격(가운데 4·기본 8).
 * 크루·마이처럼 테두리 있는 칸은 [RunkkStatTileDefaults.Outlined]에서 시작한다.
 */
@Immutable
data class StatTileStyle(
    val valueStyle: TextStyle = RunkkTextStyles.DisplayMedium,
    val valueColor: Color = Color.Unspecified,
    val isCentered: Boolean = false,
    val borderColor: Color = Color.Unspecified,
    val shape: Shape? = null,
    val contentPadding: PaddingValues? = null,
    val labelPosition: StatTileLabelPosition = StatTileLabelPosition.Top,
    val labelStyle: TextStyle? = null,
    val labelColor: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val spacing: Dp = Dp.Unspecified
)
