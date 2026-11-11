package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 프로필 원의 셀카 머리 구도([임시:D-성-189]): 셀카 머리 층(1024 캔버스, 얼굴·짧은 목)을 프로필 원 목표 구도([PortraitHeadFrame.TARGET],
 * 정수리는 위 끝에서 살짝 잘리고 턱은 86%, 가운데)에 맞추고, 목은 턱과 목 끝 사이 가운데 줄부터 상자 바닥까지 목 폭 그대로 늘린다
 * (그 아래 목 끝 외곽선은 그리지 않는다). 기본 머리 그림도 같은 구도다. 픽셀 배열만 다루는 계산을 시험한다.
 */
class PortraitHeadFrameTest {
    @Test
    fun `정수리·턱·가운데를 남녀 같은 목표 구도에 맞춘다(정수리 -5퍼센트, 턱 86퍼센트, 가운데)`() {
        val target = PortraitHeadFrame.TARGET
        assertNear(-.05f, target.crownY, "정수리는 원 위 끝에서 살짝 잘린다")
        assertNear(.86f, target.chinY, "턱은 원 높이 86퍼센트")
        assertNear(.5f, target.centerX, "가운데")
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE))
        assertNear(target.crownY, frame.mapY(CROWN.toFloat()), "정수리")
        assertNear(target.chinY, frame.mapY(CHIN.toFloat()), "턱")
        assertNear(target.centerX, frame.mapX(CENTER), "가운데")
    }

    @Test
    fun `짧은 목은 턱과 목 끝 사이 가운데 줄부터 바닥까지 잇고 목 끝 줄(외곽선)은 그리지 않는다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE))
        assertTrue(frame.mapY(NECK_BOTTOM.toFloat()) < 1f, "시험 자료의 목은 상자 바닥에 닿지 않는다")
        val row = assertNotNotNull(frame.neckRow, "목을 잇는다")
        val middle = (CHIN + NECK_BOTTOM) / 2
        assertTrue(abs(row - middle) <= 1, "이을 줄은 턱과 목 끝 사이 가운데다: $row")
        assertTrue(row < NECK_BOTTOM - 10, "목 끝 외곽선 줄은 늘리는 줄 아래라 그리지 않는다")
    }

    @Test
    fun `목이 이미 상자 바닥을 넘으면 잇지 않는다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(neckBottom = 880), hair(), SIZE, SIZE))
        assertTrue(frame.mapY(880f) >= 1f)
        assertNull(frame.neckRow)
    }

    @Test
    fun `목을 찾지 못하거나 층이 비었으면 구도를 정하지 않는다(그림을 그대로 맞춘다)`() {
        val noNeck = IntArray(SIZE * SIZE).also { fill(it, 312..712, 300 until 750) }
        assertNull(PortraitHeadFrame.of(noNeck, hair(), SIZE, SIZE))
        assertNull(PortraitHeadFrame.of(face(), IntArray(SIZE * SIZE), SIZE, SIZE))
    }

    private fun <T : Any> assertNotNotNull(value: T?, message: String = "구도를 정한다"): T = assertNotNull(value, message)

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= TOLERANCE, "$message: 기대 $expected, 실제 $actual")

    private companion object {
        const val SIZE = 1024
        const val CROWN = 150
        const val CHIN = 750
        const val NECK_BOTTOM = 824
        const val CENTER = 512f
        const val TOLERANCE = .01f
        const val OPAQUE = 0xFFF1C8B0.toInt()

        /** 셀카 머리 얼굴 층 꼴(GeneratedAvatarLayersTest와 같은 도형): 머리 x312..712·y300..750, 목 x467..557. */
        fun face(neckBottom: Int = NECK_BOTTOM) = IntArray(SIZE * SIZE).also {
            fill(it, 312..712, 300 until CHIN)
            fill(it, 467..557, CHIN until neckBottom)
        }

        /** 헤어 층(정수리 y150). */
        fun hair() = IntArray(SIZE * SIZE).also { fill(it, 300..724, CROWN..330) }

        fun fill(pixels: IntArray, xs: IntRange, ys: IntRange) {
            for (y in ys) for (x in xs) pixels[y * SIZE + x] = OPAQUE
        }
    }
}
