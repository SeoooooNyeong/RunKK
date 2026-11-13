package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 바닥 규격 계산(design-oo 05-30 4.4, [임시:D-성-107], [임시:DR-성-5], [임시:DR-성-6]). 검산 표 다섯 칸과 홈 칸(390x420,
 * [임시:D-성-134]), 지금 배경 그림(720x1440)을 그대로 셈한다. 발끝 줄(아트보드 위에서 80.4%)이 바닥선에 온다.
 */
class AvatarStageLayoutTest {
    @Test
    fun `검산 표 다섯 칸과 홈 칸의 바닥선·아트보드·위치가 설계 값과 같다`() {
        // 칸 너비, 칸 높이, drawnImageH, floorY, A, riveTop, 아트보드 너비 (그림 1080x1200, 4.3 규격)
        val rows = listOf(
            Row("내 러너", 350f, 390f, 390f, 319.8f, 351f, 37.6f, 280.8f),
            Row("꾸미기", 152f, 200f, 200f, 164f, 180f, 19.3f, 144f),
            Row("배경 고르기", 95f, 126f, 126f, 103.3f, 113.4f, 12.1f, 90.7f),
            Row("해금 팝업(너비 제약)", 280f, 500f, 500f, 410f, 350f, 128.6f, 280f),
            Row("홈", 390f, 420f, 433.3f, 342f, 378f, 38.1f, 302.4f)
        )
        for (row in rows) {
            val layout = AvatarStageLayout.of(row.slotW, row.slotH, imageWidth = 1080f, imageHeight = 1200f)
            assertNear(row.drawnImageH, layout.drawnImageHeight, "${row.name} drawnImageH")
            assertNear(row.floorY, layout.floorY, "${row.name} floorY")
            assertNear(row.artboard, layout.artboardHeight, "${row.name} A")
            assertNear(row.riveTop, layout.artboardTop, "${row.name} riveTop")
            assertNear(row.width, layout.artboardWidth, "${row.name} 너비")
            assertNear((row.slotW - row.width) / 2, layout.artboardLeft, "${row.name} 가로 가운데")
            assertNear(
                layout.floorY,
                layout.artboardTop + RiveAvatarGeometry.FOOT_LINE * layout.artboardHeight,
                "${row.name} 발끝 줄"
            )
            assertTrue(layout.artboardWidth <= row.slotW + EPSILON, "${row.name} 아트보드가 칸 옆으로 넘치지 않는다")
            assertTrue(layout.artboardTop >= 0.06f * row.slotH - EPSILON, "${row.name} 아트보드 위 여백 6% 이상")
        }
    }

    @Test
    fun `러닝 중 칸 88x88은 배경 없이 바닥선 94%, A = 1·17 × 칸 높이, 위 끝이 칸 위 끝이다`() {
        val layout = AvatarStageLayout.withoutBackdrop(88f, 88f)
        assertNear(82.72f, layout.floorY, "floorY")
        assertNear(102.96f, layout.artboardHeight, "A")
        assertNear(-0.06f, layout.artboardTop, "riveTop(≈0)")
        assertNear(82.37f, layout.artboardWidth, "너비 ≤ 88")
        assertNear(24.71f, layout.shadowWidth, "그림자 폭 25dp")
        assertNear(4.94f, layout.shadowHeight, "그림자 높이 5dp")
        assertTrue(layout.floorY + layout.shadowHeight / 2 < 88f, "그림자 아래 끝이 칸 안")
        assertEquals(null, layout.drawnImageHeight)
    }

    @Test
    fun `지금 그림 720x1440을 350x390에 깔면 위 여백 6% 규칙으로 아트보드를 줄인다`() {
        val layout = AvatarStageLayout.of(350f, 390f, imageWidth = 720f, imageHeight = 1440f)
        assertNear(700f, layout.drawnImageHeight, "drawnImageH")
        assertNear(264f, layout.floorY, "floorY")
        assertNear(299.25f, layout.artboardHeight, "A = (264 − 23.4) / 0.804")
        assertNear(23.4f, layout.artboardTop, "위 여백이 정확히 6%")
    }

    @Test
    fun `그림자는 폭 0·24A, 높이 0·048A, 중심이 칸 가운데 바닥선이다`() {
        val layout = AvatarStageLayout.of(350f, 390f, imageWidth = 1080f, imageHeight = 1200f)
        assertNear(0.24f * 351f, layout.shadowWidth, "폭")
        assertNear(0.048f * 351f, layout.shadowHeight, "높이")
        assertNear(175f, layout.shadowCenterX, "가로 가운데")
        assertNear(layout.floorY, layout.shadowCenterY, "세로 = 바닥선")
    }

    @Test
    fun `그림자 불투명도는 라이트 18%, 다크 24%다`() {
        assertEquals(0.18f, RiveAvatarGeometry.shadowAlpha(darkTheme = false))
        assertEquals(0.24f, RiveAvatarGeometry.shadowAlpha(darkTheme = true))
        assertEquals(0.8f, RiveAvatarGeometry.ASPECT)
        assertEquals(0.804f, RiveAvatarGeometry.FOOT_LINE)
    }

    @Test
    fun `뒤집기 보정은 몸 가로 위치 차이가 칸 너비 3%를 넘을 때만 한다`() {
        val run = AvatarStageLayout.withoutBackdrop(88f, 88f)
        // 승인 달리기 몸 중심(아트보드 너비의 49%)이면 차이 2% × 82.4 = 1.6dp(칸의 1.9%)라 보정하지 않는다.
        assertEquals(0f, run.mirrorShift())
        val diff = abs(1 - 2 * RiveAvatarGeometry.RUN_BODY_CENTER_X) * run.artboardWidth
        assertTrue(diff <= RiveAvatarGeometry.MIRROR_TOLERANCE * 88f, "지금 값은 허용 안")

        // 몸이 아트보드 45% 자리에 있으면 차이 10% × 82.4 = 8.2dp > 2.64dp: 뒤집힌 뷰를 (2c − 1) × 너비만큼 옮긴다.
        assertNear(-0.1f * run.artboardWidth, run.mirrorShift(bodyCenterX = 0.45f), "왼쪽으로 옮김")
        assertNear(0.1f * run.artboardWidth, run.mirrorShift(bodyCenterX = 0.55f), "오른쪽으로 옮김")
    }

    @Test
    fun `배경 그림 크기를 모르면 칸과 같은 비율로 보고 칸 아래에서 18%를 바닥선으로 둔다`() {
        val layout = AvatarStageLayout.of(350f, 390f, imageWidth = 0f, imageHeight = 0f)
        assertNear(390f, layout.drawnImageHeight, "drawnImageH = 칸 높이")
        assertNear(319.8f, layout.floorY, "floorY")
    }

    private data class Row(
        val name: String,
        val slotW: Float,
        val slotH: Float,
        val drawnImageH: Float,
        val floorY: Float,
        val artboard: Float,
        val riveTop: Float,
        val width: Float
    )

    private fun assertNear(expected: Float, actual: Float?, message: String) {
        checkNotNull(actual) { "$message: null" }
        assertTrue(abs(expected - actual) <= TOLERANCE, "$message: 기대 $expected, 실제 $actual")
    }

    private companion object {
        /** 검산 표는 소수 첫째 자리까지 적었다. */
        const val TOLERANCE = 0.06f
        const val EPSILON = 0.001f
    }
}
