package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 결과 무대 배치([임시:D-성-280], [임시:FR-성-33], [임시:NFR-성-8]): 폭이 달라도 트랙 윗선 232, 발끝 266, 아바타 높이 180이 같고 배경만
 * 가로를 채워 커진다. 카드 배치([임시:D-성-290])는 칸 안에서 같은 「트랙 가운데에 선다」를 지킨다. 좌표는 물리 화면 위쪽 끝이 0이다.
 */
class ResultStageLayoutTest {
    @Test
    fun `폭 320은 그림 높이 하한 377·8로 바닥이 300에 닿고 왼쪽이 −10이다`() {
        val layout = ResultStageLayout.forResult(320f, ASPECT)
        assertEquals(377.78f, layout.background.height, 0.01f)
        assertEquals(340f, layout.background.width, 0.01f)
        assertEquals(-10f, layout.background.left, 0.01f)
        assertEquals(-77.78f, layout.background.top, 0.01f)
        assertEquals(300f, layout.background.bottom, 0.01f)
    }

    @Test
    fun `폭 360은 트랙 윗선 232 발 266 높이 180`() {
        val layout = ResultStageLayout.forResult(360f, ASPECT)
        assertEquals(400f, layout.background.height, 0.01f)
        assertEquals(360f, layout.background.width, 0.01f)
        assertEquals(0f, layout.background.left, 0.01f)
        assertEquals(-96f, layout.background.top, 0.01f)
        assertEquals(304f, layout.background.bottom, 0.01f)
        assertEquals(232f, layout.trackTop, 0.01f)
        assertEquals(266f, layout.feetY, 0.01f)
        assertEquals(180f, layout.avatar.height, 0.01f)
        assertEquals(266f, layout.avatar.bottom, 0.01f)
    }

    @Test
    fun `폭 600은 그림 666·7에 위가 −314·7이다`() {
        val layout = ResultStageLayout.forResult(600f, ASPECT)
        assertEquals(666.67f, layout.background.height, 0.01f)
        assertEquals(600f, layout.background.width, 0.01f)
        assertEquals(0f, layout.background.left, 0.01f)
        assertEquals(-314.67f, layout.background.top, 0.01f)
        assertEquals(352f, layout.background.bottom, 0.01f)
    }

    @Test
    fun `세 폭 모두 트랙 윗선 232 발 266 높이 180이고 아바타 left가 (W−144)÷2다`() {
        listOf(320f, 360f, 600f).forEach { width ->
            val layout = ResultStageLayout.forResult(width, ASPECT)
            assertEquals(232f, layout.trackTop, 0.01f, "trackTop @$width")
            assertEquals(266f, layout.feetY, 0.01f, "feet @$width")
            assertEquals(180f, layout.avatar.height, 0.01f, "height @$width")
            assertEquals(144f, layout.avatar.width, 0.01f, "width @$width")
            assertEquals((width - 144f) / 2, layout.avatar.left, 0.01f, "left @$width")
            assertEquals(86f, layout.avatar.top, 0.01f, "top @$width")
            // 그림의 트랙 윗선(아래에서 18%)이 232에 온다.
            assertEquals(232f, layout.background.bottom - 0.18f * layout.background.height, 0.01f, "그림 트랙 @$width")
        }
    }

    @Test
    fun `그림 비율을 모르면 무대 규격 0·9로 본다`() {
        assertEquals(ResultStageLayout.forResult(360f, ASPECT), ResultStageLayout.forResult(360f, 0f))
    }

    @Test
    fun `forCard는 298×230에서 trackTop≈170 feet≈200이고 아바타 높이는 칸의 0·80이다`() {
        val layout = ResultStageLayout.forCard(298f, 230f, ASPECT)
        assertEquals(170.4f, layout.trackTop, 0.1f)
        assertEquals(200.2f, layout.feetY, 0.1f)
        assertEquals(184f, layout.avatar.height, 0.01f)
        assertEquals(layout.feetY, layout.avatar.bottom, 0.01f)
        assertEquals(149f, layout.avatar.center.x, 0.01f)
        // 그림은 가로를 채우고 아래에 맞춘다.
        assertEquals(298f, layout.background.width, 0.01f)
        assertEquals(230f, layout.background.bottom, 0.01f)
    }

    @Test
    fun `forCard는 납작한 칸에서 가로를 채우고 좁은 칸에서 가로를 넘겨 자른다`() {
        val wide = ResultStageLayout.forCard(400f, 200f, ASPECT)
        assertEquals(444.44f, wide.background.height, 0.01f)
        assertEquals(200f, wide.background.bottom, 0.01f)
        val narrow = ResultStageLayout.forCard(100f, 200f, ASPECT)
        assertEquals(200f, narrow.background.height, 0.01f)
        assertEquals(180f, narrow.background.width, 0.01f)
        assertEquals(-40f, narrow.background.left, 0.01f)
    }

    @Test
    fun `그림자는 아바타 높이 180에서 폭 67·5 높이 13·5다`() {
        val layout = ResultStageLayout.forResult(360f, ASPECT)
        assertEquals(67.5f, layout.shadowWidth, 0.01f)
        assertEquals(13.5f, layout.shadowHeight, 0.01f)
    }

    private companion object {
        const val ASPECT = 0.9f
    }
}
