package com.a207.runkk.watch.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 메인 캐러셀 움직임(피그마 Q1~Q4 Smart Animate): 칸별 모양, 칸 사이 섞기, 순환 번호, 끌기 놓을 때 붙을 칸. */
class WatchCarouselMotionTest {
    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.0001f)

    @Test
    fun `가운데 카드는 크고 라임이며 한 칸 떨어지면 작은 카드다`() {
        val center = carouselSlot(0f)
        near(0f, center.centerY)
        near(0.48f, center.width)
        near(0.28f, center.height)
        near(1f, center.emphasis)
        val below = carouselSlot(1f)
        near(0.25f, below.centerY)
        near(0.17f, below.height)
        near(0f, below.emphasis)
        near(-0.25f, carouselSlot(-1f).centerY)
    }

    @Test
    fun `두 칸은 이름 없는 흐린 장식이고 세 칸은 보이지 않는다`() {
        val far = carouselSlot(2f)
        near(0.42f, far.centerY)
        near(0.85f, far.alpha)
        near(0f, far.labelAlpha)
        near(0f, carouselSlot(-3f).alpha)
        near(0f, carouselSlot(5f).alpha)
    }

    @Test
    fun `칸 사이에서는 위치와 크기와 강조가 이어서 바뀐다`() {
        val half = carouselSlot(0.5f)
        near(0.125f, half.centerY)
        near(0.415f, half.width)
        near(0.225f, half.height)
        near(0.5f, half.emphasis)
        near(-0.125f, carouselSlot(-0.5f).centerY)
    }

    @Test
    fun `카드 번호는 네 장으로 순환한다`() {
        assertEquals(WatchMode.FREE, carouselMode(0))
        assertEquals(WatchMode.CELEB, carouselMode(3))
        assertEquals(WatchMode.FREE, carouselMode(4))
        assertEquals(WatchMode.CELEB, carouselMode(-1))
        assertEquals(WatchMode.BATTLE, carouselMode(-7))
    }

    @Test
    fun `끌기를 놓으면 가까운 칸으로 붙고 조금만 끌어도 한 칸 넘긴다`() {
        assertEquals(0, carouselSettle(0, 0.1f))
        assertEquals(1, carouselSettle(0, DRAG_SETTLE_SLOTS))
        assertEquals(-1, carouselSettle(0, -0.4f))
        assertEquals(1, carouselSettle(0, 0.6f))
        assertEquals(2, carouselSettle(0, 1.7f))
        assertEquals(3, carouselSettle(4, 3.6f))
    }
}
