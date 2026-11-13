package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 꼬리표 알약([RunkkTagPill]) 크기: 최소 높이, 좌우·위아래 여백, 글자. 모양은 모두 알약이다.
 * [XSmall] 11 Regular·8×2(카드 안 작은 태그, 기본), [Small] 12 Medium·10×4(주간 배지·주 요약 칩), [Medium] 높이 28·14×4·14 Medium
 * (크루 태그 알약).
 */
enum class RunkkTagSize(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val textStyle: TextStyle,
    val iconSize: Dp
) {
    XSmall(0.dp, 8.dp, 2.dp, RunkkTextStyles.text(11), 12.dp),
    Small(0.dp, 10.dp, 4.dp, RunkkTextStyles.text(12, FontWeight.Medium), 14.dp),
    Medium(28.dp, 14.dp, 4.dp, RunkkTextStyles.text(14, FontWeight.Medium), 16.dp)
}
