package com.a207.runkk.app.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** 말풍선 자리(루트 좌표, 픽셀): 왼쪽 위, 너비, 꼬리 방향과 꼬리 가운데의 말풍선 안 가로 위치. */
internal data class GuideBubblePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val tail: GuideTail,
    val tailX: Float
)

/**
 * 말풍선 자리를 정한다. 강조 자리([hole])가 화면([screen]) 위쪽 절반이면 그 아래 [gap]만큼 띄워 꼬리를 위로, 아래쪽이면 그 위에
 * 꼬리를 아래로 단다. 가로는 앵커 가운데에 맞추되 화면 양옆 [margin] 안에 넣고, 꼬리는 앵커 가운데를 가리킨다
 * (말풍선 모서리에서 [tailInset] 안). [bubble]은 꼬리를 포함한 말풍선 크기다.
 */
internal fun bubblePlacement(
    hole: Rect?,
    screen: Size,
    bubble: Size,
    margin: Float,
    gap: Float,
    tailInset: Float
): GuideBubblePlacement {
    val maxTop = (screen.height - bubble.height).coerceAtLeast(0f)
    if (hole == null) {
        return GuideBubblePlacement(
            left = (screen.width - bubble.width) / 2f,
            top = maxTop / 2f,
            width = bubble.width,
            tail = GuideTail.NONE,
            tailX = bubble.width / 2f
        )
    }
    val maxLeft = (screen.width - margin - bubble.width).coerceAtLeast(margin)
    val left = (hole.center.x - bubble.width / 2f).coerceIn(margin, maxLeft)
    val isBelow = hole.center.y < screen.height / 2f
    val top = if (isBelow) hole.bottom + gap else hole.top - gap - bubble.height
    val tailX = (hole.center.x - left).coerceIn(tailInset, (bubble.width - tailInset).coerceAtLeast(tailInset))
    return GuideBubblePlacement(
        left = left,
        top = top.coerceIn(0f, maxTop),
        width = bubble.width,
        tail = if (isBelow) GuideTail.UP else GuideTail.DOWN,
        tailX = tailX
    )
}
