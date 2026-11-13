package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 알림 점(피그마 RunKK / 알림 점 62268:2783): 지름 12의 라임 점과 바깥 2dp 테두리([ringColor], 기본은 표면색). 테두리는 칸 밖에 그려
 * 자리를 차지하지 않는다. 알림함 아이콘 오른쪽 위(아이콘 원 가장자리에서 중심이 4dp 안쪽)나 알림함 이벤트 행 끝에 둔다.
 * 장식이므로 읽기 문구는 부르는 쪽이 붙인다.
 */
@Composable
fun RunkkNotificationDot(modifier: Modifier = Modifier, ringColor: Color = RunkkColors.Surface) {
    val fill = RunkkColors.LimeFill
    Box(
        modifier
            .size(NOTIFICATION_DOT_SIZE)
            .drawBehind {
                val ring = NOTIFICATION_DOT_RING.toPx()
                val radius = size.minDimension / 2
                drawCircle(ringColor, radius = radius + ring, center = Offset(size.width / 2, size.height / 2))
                drawCircle(fill, radius = radius)
            }
    )
}

/** 알림 점 지름과 바깥 테두리 두께. */
val NOTIFICATION_DOT_SIZE: Dp = 12.dp
private val NOTIFICATION_DOT_RING: Dp = 2.dp

/**
 * 넘김 점(피그마 RunKK / 이벤트 / 넘김 점 62268:2845): 지금 장은 18×6 막대, 나머지는 6dp 점. 보조 기술에는 [description]
 * ("1 / 3" 등) 하나로 읽힌다. 결과 화면의 쪽 점은 [gap] 6, [inactiveColor] 강한 테두리로 부른다.
 * 점 크기는 [dotSize](기본 6, 기록 카드 K-1은 8), 지금 장 막대 길이는 [activeWidth](기본 18)다.
 */
@Composable
fun RunkkPageDots(
    current: Int,
    count: Int,
    description: String,
    modifier: Modifier = Modifier,
    gap: Dp = 5.dp,
    inactiveColor: Color = RunkkColors.Border,
    dotSize: Dp = 6.dp,
    activeWidth: Dp = 18.dp
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) activeWidth else dotSize, height = dotSize)
                    .clip(CircleShape)
                    .background(if (index == current) RunkkColors.TextPrimary else inactiveColor)
            )
        }
    }
}
