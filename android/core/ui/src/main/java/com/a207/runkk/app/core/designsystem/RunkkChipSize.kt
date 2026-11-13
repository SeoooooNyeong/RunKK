package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 고르기 칩([RunkkChoiceChip]) 크기: 보이는 높이, 좌우 여백, 글자 크기. 모양은 모두 알약이고 누르는 칸은 48dp다.
 * [Small] 32·12·13(기본, 피그마 시간대 칩), [Medium] 36·14·14(상점 부위 칩), [Large] 48·16·15(대결 거리 칩).
 */
enum class RunkkChipSize(val height: Dp, val horizontalPadding: Dp, val textSize: Int) {
    Small(32.dp, 12.dp, 13),
    Medium(36.dp, 14.dp, 14),
    Large(48.dp, 16.dp, 15)
}
