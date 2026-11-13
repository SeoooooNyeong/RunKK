package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 작은 도는 표시(글자 옆·단추 안 불러오기): 지름 [size](기본 16), 선 [strokeWidth] 2, 색 [color](기본 보라), 바탕 고리 [trackColor]
 * (기본 없음). [contentDescription]이 있으면 보조 기술에 "불러오는 중" 진행으로 알리고, 없으면 장식이라 숨긴다.
 * 화면·구역 전체를 채우는 불러오기는 [RunkkLoading]을 쓴다.
 */
@Composable
fun RunkkSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = RunkkColors.Violet,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 2.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth
        )
    }
}
