package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 구역 머리([RunkkSectionHeader]) 글자 크기: 제목과 개수 글자.
 * [Small] 13 Bold·13(기록 목록), [Compact] 14/17 Bold·13(크루 찾기·레이스), [Medium] 15/18 Bold·13/18(크루 구역 제목, 기본),
 * [Card] 16 Bold·13(대결 입장 구역), [Large] 17 Bold·17 Bold(이벤트 목록).
 */
enum class RunkkSectionHeaderSize(val titleStyle: TextStyle, val countStyle: TextStyle) {
    Small(RunkkTextStyles.text(13, FontWeight.Bold), RunkkTextStyles.text(13)),
    Compact(RunkkTextStyles.text(14, FontWeight.Bold, lineHeight = 17), RunkkTextStyles.text(13, lineHeight = 17)),
    Medium(RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18), RunkkTextStyles.text(13, lineHeight = 18)),
    Card(RunkkTextStyles.CardTitle, RunkkTextStyles.text(13)),
    Large(RunkkTextStyles.text(17, FontWeight.Bold), RunkkTextStyles.text(17, FontWeight.Bold))
}
