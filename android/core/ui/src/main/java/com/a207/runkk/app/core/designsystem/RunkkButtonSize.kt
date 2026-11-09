package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 알약 버튼 크기(피그마 RunKK / Buttons / 기본 Size). 높이·좌우 여백·글자를 함께 정한다. 모서리는 알약이다.
 * 화면 맨 아래 주 버튼은 [Large], 대화상자·바텀시트 버튼은 [Medium], 줄 안의 작은 행동은 [Small]·[XSmall].
 * [Hero]는 홈 "자유런 시작"처럼 큰 주 버튼(72, 모서리 20, 피그마 기본 버튼 밖)이다.
 */
enum class RunkkButtonSize(val height: Dp, val horizontalPadding: Dp, val textStyle: TextStyle, val shape: Shape) {
    Large(52.dp, 16.dp, RunkkTextStyles.text(16, FontWeight.SemiBold), RunkkShapes.Pill),
    Medium(48.dp, 16.dp, RunkkTextStyles.text(15, FontWeight.SemiBold), RunkkShapes.Pill),
    Small(36.dp, 16.dp, RunkkTextStyles.text(14, FontWeight.Medium), RunkkShapes.Pill),
    XSmall(28.dp, 10.dp, RunkkTextStyles.text(12, FontWeight.Medium), RunkkShapes.Pill),
    Hero(72.dp, 16.dp, RunkkTextStyles.text(24, FontWeight.Bold), RoundedCornerShape(20.dp))
}
