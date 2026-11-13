package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 내장 샘플 옆얼굴의 목 등록(PoC tools/latest-side-2026-11-09/run-jvm-test.ps1): 목만 줄고 얼굴 줄과 캔버스는 그대로다.
 */
class SideNeckRegistrationTest {
    @Test
    fun `목 위 얼굴 줄은 그대로 두고 목 구간만 0·55배로 줄인다`() {
        for (registration in SideNeckRegistration.entries) {
            val vertices = registration.meshVertices()
            assertEquals(1025 * 4, vertices.size)
            for (sourceRow in 0..registration.startY.toInt()) {
                for (column in 0..1) {
                    val vertex = sourceRow * 4 + column * 2
                    assertEquals(column * 1024f, vertices[vertex])
                    assertEquals(sourceRow.toFloat(), vertices[vertex + 1])
                }
            }
            val bottomLeft = registration.endY.toInt() * 4
            val bottomRight = bottomLeft + 2
            assertTrue(abs(vertices[bottomRight] - vertices[bottomLeft] - 1024f * .55f) < .001f)
            assertTrue(abs(vertices[bottomLeft + 1] - (registration.startY + 72f * .55f)) < .001f)
            assertTrue(abs(registration.offsetY - 32.4f) < .001f)
        }
    }

    @Test
    fun `목 아래 떨어진 점은 지우고 이어진 목과 가장자리 한 픽셀은 남긴다`() {
        for (registration in SideNeckRegistration.entries) {
            val pixels = IntArray(1024 * 1024)
            pixels[0] = 0x12345678
            val start = registration.startY.toInt()
            for (y in start..start + 20) for (x in 600..610) pixels[y * 1024 + x] = 0xffdec0a0.toInt()
            pixels[(start + 10) * 1024 + 599] = 0x40dec0a0
            pixels[(start + 30) * 1024 + 650] = 0xffdec0a0.toInt()
            val cleaned = registration.cleanNeckPixels(pixels)
            assertEquals(pixels[0], cleaned[0])
            assertEquals(pixels[(start + 10) * 1024 + 605], cleaned[(start + 10) * 1024 + 605])
            assertEquals(pixels[(start + 10) * 1024 + 599], cleaned[(start + 10) * 1024 + 599])
            assertEquals(0, cleaned[(start + 30) * 1024 + 650])
        }
    }

    @Test
    fun `등록값은 내장 샘플 남녀 옆얼굴 둘뿐이다`() {
        assertEquals(
            setOf(AvatarGender.FEMALE, AvatarGender.MALE),
            SideNeckRegistration.entries.map {
                it.gender
            }.toSet()
        )
        assertEquals(SideNeckRegistration.MALE, SideNeckRegistration.builtInFor(AvatarGender.MALE))
        assertEquals(SideNeckRegistration.FEMALE, SideNeckRegistration.builtInFor(AvatarGender.FEMALE))
    }
}
