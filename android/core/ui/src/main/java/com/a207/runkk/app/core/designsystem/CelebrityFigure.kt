package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 셀럽 공통 SD 그림(D-254). 모든 셀럽이 같은 그림 하나를 쓴다(셀럽런 목록·카운트다운·진행 마커·결과). 그림 자원(디자인 SD 캐릭터)이
 * 들어오기 전에는 라벤더 원 안의 "SD" 자리를 그린다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun CelebrityFigure(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RunkkColors.Border)
            .border(2.dp, RunkkColors.Lavender, CircleShape)
            .testTag(CELEBRITY_FIGURE_TAG)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            "SD",
            style = RunkkTextStyles.display((size.value / 3).toInt().coerceAtLeast(10)),
            color = RunkkColors.Lavender
        )
    }
}

/** 셀럽 SD 그림의 시험 표지. */
const val CELEBRITY_FIGURE_TAG = "celebrity-figure"
