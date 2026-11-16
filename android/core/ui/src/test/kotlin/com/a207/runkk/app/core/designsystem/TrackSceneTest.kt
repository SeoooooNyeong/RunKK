package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 카드 높이와 달리기 속도를 실제 레인 치수와 장식의 이동량으로 바꾸는 계산을 검증한다. */
class TrackSceneTest {
    @Test
    fun `328 카드에서 레인과 자를 고정하고 아바타 발을 각 레인에 맞춘다`() {
        val scene = TrackScene.dimensions(328f)

        assertEquals(164f, scene.skyHeight)
        assertEquals(138f, scene.laneHeight)
        assertEquals(26f, scene.rulerHeight)
        assertEquals(105f, scene.mySize)
        assertEquals(88f, scene.otherSize)
        assertEquals(293f, scene.myFootY)
        assertEquals(225f, scene.otherFootY)
        assertEquals(28f, scene.tagHeight)
    }

    @Test
    fun `280과 240 카드에서도 레인과 발 위치를 유지하고 이름표가 전환 아래에 남는다`() {
        listOf(280f to 116f, 240f to 76f).forEach { (height, sky) ->
            val scene = TrackScene.dimensions(height)

            assertEquals(sky, scene.skyHeight)
            assertEquals(138f, scene.laneHeight)
            assertEquals(26f, scene.rulerHeight)
            assertEquals(sky + 129f, scene.myFootY)
            assertEquals(sky + 61f, scene.otherFootY)
            assertTrue(scene.otherTagY >= 96.99f)
            assertEquals(scene.mySize / 105f, scene.otherSize / 88f, 0.001f)
            assertTrue(scene.mySize < 105f)
        }
    }

    @Test
    fun `328 280 240 카드에서 보이는 머리 위 4dp에 이름표를 놓고 두 이름표가 겹치지 않는다`() {
        listOf(328f, 280f, 240f).forEach { height ->
            val scene = TrackScene.dimensions(height)
            assertEquals(4f, scene.myFootY - scene.mySize - scene.myTagY - scene.tagHeight, 0.001f)
            assertEquals(4f, scene.otherFootY - scene.otherSize - scene.otherTagY - scene.tagHeight, 0.001f)
            assertTrue(scene.otherTagY + scene.tagHeight < scene.myTagY)
        }
    }

    @Test
    fun `Rive 칸의 실제 채움 비율을 보정해 머리와 발을 보이는 칸에 맞춘다`() {
        listOf(328f, 280f, 240f).forEach { height ->
            val scene = TrackScene.dimensions(height)
            listOf(scene.mySize, scene.otherSize).forEach { visible ->
                val viewport = TrackAvatarViewport.of(visible)
                assertEquals(visible, viewport.slotSize * 0.7488f, 0.001f)
                assertEquals(visible, viewport.top + viewport.slotSize * 0.94f, 0.001f)
                assertEquals(0f, viewport.top + viewport.slotSize * (0.94f - 0.7488f), 0.001f)
            }
        }
    }

    @Test
    fun `점선은 속도와 화면 폭에 비례해 왼쪽으로 흐르고 한 주기 뒤 이어진다`() {
        val travel = TrackScene.advanceTravel(0f, 1f, 3f, isMoving = true, reduceMotion = false)

        assertEquals(3f, travel)
        assertEquals(-35f, TrackScene.dashOffset(travel, 350f), 0.001f)
        assertEquals(-22f, TrackScene.dashOffset(travel * 2, 350f), 0.001f)
        assertEquals(0f, TrackScene.dashOffset(0f, 350f))
        assertEquals(-24f, TrackScene.dashOffset(2.4f, 300f), 0.001f)
    }

    @Test
    fun `정지와 움직임 줄이기에서는 이동량을 보존하고 잘못된 속도는 움직이지 않는다`() {
        assertEquals(7f, TrackScene.advanceTravel(7f, 1f, 3f, isMoving = false, reduceMotion = false))
        assertEquals(7f, TrackScene.advanceTravel(7f, 1f, 3f, isMoving = true, reduceMotion = true))
        assertEquals(7f, TrackScene.advanceTravel(7f, 1f, Float.NaN, isMoving = true, reduceMotion = false))
        assertEquals(7f, TrackScene.advanceTravel(7f, 1f, -1f, isMoving = true, reduceMotion = false))
    }

    @Test
    fun `속도 0과 양쪽 무한대와 음수 시간에는 이동하지 않는다`() {
        listOf(0f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { speed ->
            assertEquals(7f, TrackScene.advanceTravel(7f, 1f, speed, isMoving = true, reduceMotion = false))
        }
        assertEquals(7f, TrackScene.advanceTravel(7f, -1f, 3f, isMoving = true, reduceMotion = false))
        assertEquals(7f, TrackScene.advanceTravel(7f, 0f, 3f, isMoving = true, reduceMotion = false))
    }
}
