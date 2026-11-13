package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 목 메타데이터(mobile.neckMask)가 없을 때 얼굴 알파에서 목·턱을 찾는 대체 경로(PoC tools/selfie-flow-2026-11-09/run-geometry-test.ps1).
 */
class GeneratedHeadGeometryTest {
    private val width = 1024

    private fun fixture(center: Int, chin: Int, bottom: Int, skin: Int): IntArray {
        val pixels = IntArray(width * width)
        for (y in 300 until chin) for (x in center - 220..center + 220) pixels[y * width + x] = skin
        for (y in chin until bottom) for (x in center - 45..center + 45) pixels[y * width + x] = skin
        // 떨어진 불투명 점은 목이나 중심을 바꾸지 않는다.
        pixels[950 * width + 30] = 0xff221100.toInt()
        return pixels
    }

    @Test
    fun `목 중심·턱·바닥을 찾고 턱 위 불투명 피부색 중앙값을 낸다`() {
        for ((center, chin, bottom) in listOf(Triple(512, 750, 824), Triple(470, 830, 906))) {
            val pixels = fixture(center, chin, bottom, SKIN)
            val neck = GeneratedHeadGeometry.detect(pixels, width, width)
            assertTrue(abs(neck.centerX - center) < 2f, "중심 ${neck.centerX}")
            assertTrue(abs(neck.chinY - chin) < 3f, "턱 ${neck.chinY}")
            assertEquals(bottom, neck.bottom)
            assertEquals(SKIN, GeneratedHeadGeometry.skinColor(pixels, width, width, neck.centerX, neck.chinY))
        }
    }

    @Test
    fun `빈 레이어는 목도 피부색도 찾지 못해 실패한다`() {
        val transparent = IntArray(width * width)
        assertFailsWith<IllegalArgumentException> { GeneratedHeadGeometry.detect(transparent, width, width) }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadGeometry.skinColor(transparent, width, width, 512f, 750f)
        }
    }

    @Test
    fun `넓은 턱에서 잘린 얼굴은 짧은 목으로 보지 않는다`() {
        val noNeck = IntArray(width * width)
        for (y in 300..800) for (x in 300..720) noNeck[y * width + x] = 0xfff4d4c0.toInt()
        assertFailsWith<IllegalArgumentException> { GeneratedHeadGeometry.detect(noNeck, width, width) }
    }

    @Test
    fun `크기가 맞지 않는 픽셀 배열은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { GeneratedHeadGeometry.detect(IntArray(10), width, width) }
    }

    private companion object {
        const val SKIN = 0xff8d6048.toInt()
    }
}
