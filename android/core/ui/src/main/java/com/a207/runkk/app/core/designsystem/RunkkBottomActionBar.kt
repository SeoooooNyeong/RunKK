package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 하단 고정 행동 바(피그마 61355:2909 대결 하단·61326:1878 결과 하단): 표면, 위쪽 둥근 20, 그림자([RunkkElevation.Medium],
 * [RunkkColors.Shadow]), 안쪽 좌우 20·위 12·아래 24. 화면은 스크롤 영역 밖 아래에 두고 안에 주 버튼(`RunkkButtonSize.Bar`)을 넣는다.
 * 그림자 없는 바(공유 대상)는 [isElevated]를 끄고, 모양·안쪽은 [shape]·[contentPadding]으로 바꾼다. [windowInsets]를 넘기면
 * (`WindowInsets.navigationBars`) 바탕 안쪽에 시스템 막대 높이만큼 더 띄운다.
 *
 * ```
 * RunkkBottomActionBar {
 *     RunkkButton("매칭 시작", onClick = onStart, style = RunkkButtonStyle.Purple, size = RunkkButtonSize.Bar,
 *         modifier = Modifier.fillMaxWidth())
 * }
 * ```
 */
@Composable
fun RunkkBottomActionBar(
    modifier: Modifier = Modifier,
    isElevated: Boolean = true,
    shape: Shape = BOTTOM_ACTION_BAR_SHAPE,
    background: Color = RunkkColors.Surface,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    elevation: Dp = RunkkElevation.Medium,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shadowColor = RunkkColors.Shadow
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isElevated) {
                    Modifier.shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(background)
            .windowInsetsPadding(windowInsets)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** 하단 바 모양(위쪽 둥근 20). */
private val BOTTOM_ACTION_BAR_SHAPE: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
