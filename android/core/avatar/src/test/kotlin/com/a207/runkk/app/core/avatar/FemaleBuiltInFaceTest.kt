package com.a207.runkk.app.core.avatar

import android.graphics.BitmapFactory
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * S15P31A207-306: 기본 여자 얼굴은 남자 얼굴 그림을 목 끝에 맞춰 옮긴 뒤 [SCALE]배로 줄인 것이다. 여자 몸체가 머리 그림을 남자보다
 * 약 10.8% 크게 그리므로, 그림을 그만큼 줄여야 화면에서 남녀 얼굴 크기가 같다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FemaleBuiltInFaceTest {
    @Test
    fun `정면 눈과 볼은 남자 그림의 0점903배다`() {
        for (name in listOf("eyes_open", "eyes_half", "eyes_closed", "front_fullness_1", "front_fullness_2")) {
            assertScaled(name)
        }
    }

    @Test
    fun `측면 눈과 볼도 같은 배율이다`() {
        for (name in listOf("side_eyes", "side_fullness_1", "side_fullness_2")) {
            assertScaled(name)
        }
    }

    @Test
    fun `목 끝과 목 맞춤값은 몸체 자리 그대로다`() {
        // 목 맞춤은 목 길이 72를 전제로 한다(SideNeckRegistrationTest). 그림을 줄여도 구간은 그대로 둔다.
        assertEquals(791f, SideNeckRegistration.FEMALE.startY)
        assertEquals(863f, SideNeckRegistration.FEMALE.endY)
        assertEquals(651f, SideNeckRegistration.FEMALE.centerX)
        val front = bounds("female", "face")
        val side = bounds("female", "side_face")
        assertTrue(abs(front.bottom - 865) <= 3, "정면 목 끝 ${front.bottom}")
        assertTrue(abs(side.bottom - 865) <= 3, "측면 목 끝 ${side.bottom}")
    }

    private fun assertScaled(name: String) {
        val male = bounds("male", name)
        val female = bounds("female", name)
        val width = female.width.toFloat() / male.width
        val height = female.height.toFloat() / male.height
        assertTrue(abs(width - SCALE) < TOLERANCE, "$name 가로 배율 $width")
        assertTrue(abs(height - SCALE) < TOLERANCE, "$name 세로 배율 $height")
    }

    private class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
    }

    /** 불투명도가 절반을 넘는 픽셀의 경계 상자. */
    private fun bounds(gender: String, name: String): Bounds {
        val bitmap = RuntimeEnvironment.getApplication().assets.open("avatars/$gender/$name.png").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            assertEquals(1024, bitmap.width)
            assertEquals(1024, bitmap.height)
            val pixels = IntArray(1024 * 1024).also { bitmap.getPixels(it, 0, 1024, 0, 0, 1024, 1024) }
            var left = 1024
            var top = 1024
            var right = -1
            var bottom = -1
            for (index in pixels.indices) {
                if (pixels[index] ushr 24 > 127) {
                    val x = index % 1024
                    val y = index / 1024
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            return Bounds(left, top, right, bottom)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val SCALE = .903f
        const val TOLERANCE = .02f
    }
}
