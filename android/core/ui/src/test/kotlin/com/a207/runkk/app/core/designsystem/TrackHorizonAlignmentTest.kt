package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackHorizonAlignmentTest {
    @Test
    fun `바닥선이 칸 바닥에 온다`() {
        // 너비 1050에 맞춘 무대 그림 높이 1167, 바닥선 957. 칸 높이 400이면 위로 557 올린다.
        assertEquals(-557, trackHorizonTop(imageHeight = 1167, boxHeight = 400))
    }

    @Test
    fun `칸이 바닥선보다 높으면 그림 위쪽을 칸 위에 붙인다`() {
        assertEquals(0, trackHorizonTop(imageHeight = 1000, boxHeight = 900))
    }

    @Test
    fun `그림이 칸보다 낮아도 아래로 밀지 않는다`() {
        assertEquals(0, trackHorizonTop(imageHeight = 300, boxHeight = 400))
    }

    @Test
    fun `가로는 가운데에 둔다`() {
        val offset = TrackHorizonAlignment.align(IntSize(1200, 1167), IntSize(1050, 400), LayoutDirection.Ltr)
        assertEquals(IntOffset(-75, -557), offset)
    }
}
