package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 셀럽 공통 그림(D-254, 결정 D74). 모든 셀럽이 피그마 셀럽런 결과(E-6)의 흐린 캐릭터 하나를 쓴다(셀럽런 목록·카운트다운·진행 마커·결과).
 * 실제 인물과 닮지 않게 흐리게 그린다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun CelebrityFigure(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Image(
        painter = painterResource(R.drawable.celebrity_figure),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .alpha(CELEBRITY_FIGURE_ALPHA)
            .testTag(CELEBRITY_FIGURE_TAG)
            .clearAndSetSemantics {}
    )
}

/** 셀럽 그림의 시험 표지. */
const val CELEBRITY_FIGURE_TAG = "celebrity-figure"

private const val CELEBRITY_FIGURE_ALPHA = 0.6f
