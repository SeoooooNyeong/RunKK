package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 프로필 원의 셀카 머리 구도([임시:D-성-189]): 셀카 머리 층(1024 캔버스, 얼굴·짧은 목)을 기본 머리 그림과 같은 크기·자리(정수리·턱·가운데)에
 * 맞추고, 목이 원 아래 가장자리에 닿지 않으면 목 끝 줄을 아래로 이어 상자 바닥에서 잘리게 한다. 픽셀 배열만 다루는 계산을 시험한다.
 */
class PortraitHeadFrameTest {
    @Test
    fun `정수리·턱·가운데를 기본 머리 그림 구도에 맞춘다(남녀 각각)`() {
        for (character in AvatarCharacter.entries) {
            val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE, character))
            val reference = PortraitHeadFrame.referenceOf(character)
            assertNear(reference.crownY, frame.mapY(CROWN.toFloat()), "$character 정수리")
            assertNear(reference.chinY, frame.mapY(CHIN.toFloat()), "$character 턱")
            assertNear(reference.centerX, frame.mapX(CENTER), "$character 가운데")
        }
    }

    @Test
    fun `짧은 목은 끝 줄을 상자 바닥까지 이어 목이 원 아래 가장자리에서 잘린다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE, AvatarCharacter.MALE))
        assertTrue(frame.mapY(NECK_BOTTOM.toFloat()) < 1f, "시험 자료의 목은 상자 바닥에 닿지 않는다")
        val row = assertNotNotNull(frame.neckRow, "목 끝 줄을 잇는다")
        assertTrue(row in CHIN until NECK_BOTTOM, "이을 줄은 턱 아래 목 안이다: $row")
    }

    @Test
    fun `목이 이미 상자 바닥을 넘으면 잇지 않는다`() {
        val frame =
            assertNotNotNull(PortraitHeadFrame.of(face(neckBottom = 920), hair(), SIZE, SIZE, AvatarCharacter.MALE))
        assertTrue(frame.mapY(920f) >= 1f)
        assertNull(frame.neckRow)
    }

    @Test
    fun `목을 찾지 못하거나 층이 비었으면 구도를 정하지 않는다(그림을 그대로 맞춘다)`() {
        val noNeck = IntArray(SIZE * SIZE).also { fill(it, 312..712, 300 until 750) }
        assertNull(PortraitHeadFrame.of(noNeck, hair(), SIZE, SIZE, AvatarCharacter.MALE))
        assertNull(PortraitHeadFrame.of(face(), IntArray(SIZE * SIZE), SIZE, SIZE, AvatarCharacter.MALE))
    }

    @Test
    fun `기준 구도는 기본 머리 그림에서 잰 값이다(턱은 상자의 79퍼센트 안팎, 정수리는 위 끝 안팎)`() {
        val male = PortraitHeadFrame.referenceOf(AvatarCharacter.MALE)
        val female = PortraitHeadFrame.referenceOf(AvatarCharacter.FEMALE)
        listOf(male, female).forEach {
            assertTrue(it.chinY in .78f..0.80f, "턱 ${it.chinY}")
            assertTrue(it.crownY in -.03f..0.03f, "정수리 ${it.crownY}")
            assertTrue(it.centerX in .49f..0.51f, "가운데 ${it.centerX}")
        }
        assertEquals(male.chinY, PortraitHeadFrame.referenceOf(AvatarCharacter.MALE).chinY)
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
