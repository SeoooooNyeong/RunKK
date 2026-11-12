package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 프로필 원의 셀카 머리 구도([임시:D-성-189], 사용자 요청 2026-11-12): 셀카 머리 층(1024 캔버스, 얼굴·짧은 목)을 머리 크기·가운데
 * ([PortraitHeadFrame.TARGET], 정수리~턱 91%, 가운데)에 맞추고, 원 안이면 목을 늘리지 않고 머리를 내려 목이 둥글게 끝나기 시작하는 줄이
 * 상자 바닥에 오게 한다(목 끝 외곽선은 원 아래 가장자리 밖). 원 밖이면 정수리 -5%·턱 86% 자리에 목이 제 길이로 끝난다. 기본 머리 그림도
 * 같은 구도다. 픽셀 배열만 다루는 계산을 시험한다.
 */
class PortraitHeadFrameTest {
    @Test
    fun `원 안 - 머리 크기와 가운데는 그대로 두고 머리를 내려 목 끝이 상자 바닥에 온다(목은 늘리지 않는다)`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE))
        val target = PortraitHeadFrame.TARGET
        assertNear(target.chinY - target.crownY, frame.mapY(CHIN.toFloat()) - frame.mapY(CROWN.toFloat()), "정수리~턱")
        assertNear(target.centerX, frame.mapX(CENTER), "가운데")
        assertNear(1f, frame.mapY(NECK_BOTTOM.toFloat()), "목 끝이 상자 바닥")
        assertNull(frame.neckRow, "목은 늘리지 않는다")
        val crown = frame.mapY(CROWN.toFloat())
        assertTrue(crown > target.crownY + .02f, "정수리가 원래 자리(-5%)보다 내려온다: $crown")
        assertTrue(crown in PortraitHeadFrame.CROWN_MIN..PortraitHeadFrame.CROWN_MAX, "정수리 범위: $crown")
    }

    @Test
    fun `원 안 - 목이 둥글게 끝나는 부분(외곽선)은 상자 밖이다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(roundEnd = true), hair(), SIZE, SIZE))
        assertNear(1f, frame.mapY(ROUND_START.toFloat()), "목이 좁아지기 시작하는 줄이 상자 바닥")
        assertTrue(frame.mapY(NECK_BOTTOM.toFloat()) > 1f, "둥근 끝은 상자 아래로 잘린다")
        assertNull(frame.neckRow)
    }

    @Test
    fun `원 밖 - 정수리 -5퍼센트, 턱 86퍼센트 자리에 목이 제 길이로 끝난다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE, isInCircle = false))
        val target = PortraitHeadFrame.TARGET
        assertNear(target.crownY, frame.mapY(CROWN.toFloat()), "정수리")
        assertNear(target.chinY, frame.mapY(CHIN.toFloat()), "턱")
        assertNear(target.centerX, frame.mapX(CENTER), "가운데")
        assertTrue(frame.mapY(NECK_BOTTOM.toFloat()) < 1f, "목 끝이 상자 안")
        assertNull(frame.neckRow)
    }

    @Test
    fun `목이 길면 정수리를 아래 범위 끝에 두고 목은 상자 아래에서 잘린다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(neckBottom = 900), hair(), SIZE, SIZE))
        assertNear(PortraitHeadFrame.CROWN_MIN, frame.mapY(CROWN.toFloat()), "정수리")
        assertTrue(frame.mapY(900f) > 1f)
        assertNull(frame.neckRow)
    }

    @Test
    fun `줄여 읽은 그림에 맞춘 구도는 상자에 같은 자리로 놓인다`() {
        val frame = assertNotNotNull(PortraitHeadFrame.of(face(), hair(), SIZE, SIZE))
        val half = frame.scaledBy(2f)
        assertNear(frame.mapY(CHIN.toFloat()), half.mapY(CHIN / 2f), "턱")
        assertNear(frame.mapX(CENTER), half.mapX(CENTER / 2f), "가운데")
        assertSame(frame, frame.scaledBy(1f))
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

        /** 둥근 목 끝이 시작하는 줄(그 아래는 목이 좁아진다). */
        const val ROUND_START = 800
        const val CENTER = 512f
        const val TOLERANCE = .01f
        const val OPAQUE = 0xFFF1C8B0.toInt()

        /**
         * 셀카 머리 얼굴 층 꼴(GeneratedAvatarLayersTest와 같은 도형): 머리 x312..712·y300..750, 목 x467..557. [roundEnd]면 목 끝
         * [ROUND_START]부터 폭이 줄어든다(실제 머리 그림처럼 둥근 끝).
         */
        fun face(neckBottom: Int = NECK_BOTTOM, roundEnd: Boolean = false) = IntArray(SIZE * SIZE).also {
            fill(it, 312..712, 300 until CHIN)
            if (!roundEnd) {
                fill(it, 467..557, CHIN until neckBottom)
                return@also
            }
            fill(it, 467..557, CHIN until ROUND_START)
            for (y in ROUND_START until neckBottom) {
                val inset = (y - ROUND_START) / 2 + 2
                fill(it, 467 + inset..557 - inset, y..y)
            }
        }

        /** 헤어 층(정수리 y150). */
        fun hair() = IntArray(SIZE * SIZE).also { fill(it, 300..724, CROWN..330) }

        fun fill(pixels: IntArray, xs: IntRange, ys: IntRange) {
            for (y in ys) for (x in xs) pixels[y * SIZE + x] = OPAQUE
        }
    }
}
