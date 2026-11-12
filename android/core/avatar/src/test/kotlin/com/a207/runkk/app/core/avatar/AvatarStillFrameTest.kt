package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 전신 정지 그림의 자르기·줄이기 계산과 캐시 키(랭킹 TOP3·기록런 VS 칸, S15P31A207-300). 그림 읽기는 Rive 네이티브가 있어야 해
 * 기기에서 보고, 여기서는 읽은 픽셀을 다루는 순수 계산만 본다.
 */
class AvatarStillFrameTest {
    @Test
    fun `불투명 픽셀을 감싸는 가장 작은 사각형을 찾는다`() {
        val width = 6
        val height = 5
        val pixels = IntArray(width * height)
        pixels[1 * width + 2] = OPAQUE
        pixels[3 * width + 4] = OPAQUE
        assertEquals(AvatarStillFrame.Bounds(2, 1, 5, 4), AvatarStillFrame.opaqueBounds(pixels, width, height))
    }

    @Test
    fun `거의 투명한 픽셀은 몸으로 보지 않고 다 비었으면 null이다`() {
        val pixels = IntArray(4 * 4) { FAINT }
        assertNull(AvatarStillFrame.opaqueBounds(pixels, 4, 4))
    }

    @Test
    fun `칸 안에 비율을 지켜 맞추고 키우지는 않는다`() {
        // 세로가 긴 몸(100x300)을 60x120 칸에: 높이에 맞춰 40x120
        assertEquals(AvatarStillFrame.Size(40, 120), AvatarStillFrame.fitInside(100, 300, 60, 120))
        // 가로가 긴 그림(300x100)을 60x120 칸에: 너비에 맞춰 60x20
        assertEquals(AvatarStillFrame.Size(60, 20), AvatarStillFrame.fitInside(300, 100, 60, 120))
        // 이미 칸보다 작으면 그대로 둔다(흐려지지 않게 키우지 않는다)
        assertEquals(AvatarStillFrame.Size(30, 50), AvatarStillFrame.fitInside(30, 50, 60, 120))
    }

    @Test
    fun `줄여도 한 변이 0이 되지 않는다`() {
        assertEquals(AvatarStillFrame.Size(1, 120), AvatarStillFrame.fitInside(1, 1000, 60, 120))
    }

    @Test
    fun `캐시 키는 모습과 칸 크기가 같으면 같고 머리는 참조로 가른다`() {
        val look = RiveAvatarLook(AvatarGender.MALE, AvatarOutfit(), AvatarBodyShape.BASE, 0, null)
        assertEquals(AvatarStillKey.of(look, 100, 200), AvatarStillKey.of(look.copy(), 100, 200))
        assertNotEquals(AvatarStillKey.of(look, 100, 200), AvatarStillKey.of(look, 120, 200))
        assertNotEquals(
            AvatarStillKey.of(look, 100, 200),
            AvatarStillKey.of(look.copy(gender = AvatarGender.FEMALE), 100, 200)
        )
        assertNotEquals(
            AvatarStillKey.of(look, 100, 200),
            AvatarStillKey.of(look.copy(outfit = AvatarOutfit(top = 1)), 100, 200)
        )
        // 러닝량은 렌더러처럼 0..100으로 잘라 본다
        assertEquals(
            AvatarStillKey.of(look.copy(runningAmount = 100), 1, 1),
            AvatarStillKey.of(look.copy(runningAmount = 150), 1, 1)
        )
    }

    private companion object {
        const val OPAQUE = 0xFF336699.toInt()
        const val FAINT = 0x04FFFFFF
    }

    @Test
    fun `자르는 틀은 남녀 몸 둘레와 상관없이 같은 크기이고 발에 붙어 키 차이가 그대로 남는다`() {
        // 같은 아트보드 그림(400×1000)에서 남자(좁고 키 큼)와 여자(머리로 넓고 키 조금 작음)
        val male = AvatarStillFrame.Bounds(140, 380, 260, 980)
        val female = AvatarStillFrame.Bounds(120, 400, 280, 980)
        val a = AvatarStillFrame.window(male, 400, 1000, 60, 120)
        val b = AvatarStillFrame.window(female, 400, 1000, 60, 120)
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        assertEquals(980, a.bottom)
        assertEquals(980, b.bottom)
        // 틀은 아트보드 가운데에 놓인다(몸 가운데로 옮기지 않아 남녀가 같은 자리에 선다)
        assertEquals(400 - a.right, a.left)
    }
}
