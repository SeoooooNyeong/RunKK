package com.a207.runkk.app.core.avatar

import android.graphics.BitmapFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** S15P31A207-306: 기본 여자 얼굴은 남자 원본을 목 끝에 맞춰 평행 이동한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FemaleBuiltInFaceTest {
    @Test
    fun `정면 얼굴과 눈과 볼은 같은 이동만 적용한다`() {
        for (name in listOf("face", "eyes_open", "eyes_half", "eyes_closed", "front_fullness_1", "front_fullness_2")) {
            assertTranslated(name, dx = 14, dy = -67, fillAbove = if (name == "face") 516 else 0)
        }
    }

    @Test
    fun `측면 얼굴과 눈과 볼도 목 중심을 맞춘 같은 이동만 적용한다`() {
        for (name in listOf("side_face", "side_eyes", "side_fullness_1", "side_fullness_2")) {
            assertTranslated(name, dx = 40, dy = -67, fillAbove = if (name == "side_face") 515 else 0)
        }
        assertEquals(791f, SideNeckRegistration.FEMALE.startY)
        assertEquals(863f, SideNeckRegistration.FEMALE.endY)
        assertEquals(651f, SideNeckRegistration.FEMALE.centerX)
    }

    private fun assertTranslated(name: String, dx: Int, dy: Int, fillAbove: Int) {
        val male = pixels("male", name)
        val female = pixels("female", name)
        var differences = 0
        var clipped = 0
        for (y in 0 until 1024) {
            for (x in 0 until 1024) {
                val targetX = x + dx
                val targetY = y + dy
                val source = male[y * 1024 + x]
                if (targetX !in 0 until 1024 || targetY !in 0 until 1024) {
                    if (source ushr 24 > 0) clipped++
                } else if (targetY >= fillAbove && source != female[targetY * 1024 + targetX]) {
                    differences++
                }
            }
        }
        assertEquals(0, clipped, "$name 캔버스 밖 픽셀")
        assertEquals(0, differences, "$name 평행 이동 외 변경 픽셀")
    }

    private fun pixels(gender: String, name: String): IntArray {
        val bitmap = RuntimeEnvironment.getApplication().assets.open("avatars/$gender/$name.png").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            assertEquals(1024, bitmap.width)
            assertEquals(1024, bitmap.height)
            assertTrue(bitmap.hasAlpha())
            return IntArray(1024 * 1024).also { bitmap.getPixels(it, 0, 1024, 0, 0, 1024, 1024) }
        } finally {
            bitmap.recycle()
        }
    }
}
