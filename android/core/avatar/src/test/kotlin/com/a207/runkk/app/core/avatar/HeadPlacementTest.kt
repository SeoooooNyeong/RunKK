package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 생성 머리 배치(PoC GeneratedAvatarLayers.placement): 얼굴·눈·헤어가 함께 쓰는 한 변환이 턱·중심·정수리를 몸체 목표 자리로 옮긴다.
 * 목표는 몸체(남녀·정면/측면) 값이고 특정 인물 좌표가 아니다.
 */
class HeadPlacementTest {
    @Test
    fun `여자 생성 머리 목표도 새 기본 머리의 정수리와 측면 턱에 맞춘다`() {
        assertEquals(102f, HeadTarget.FEMALE_FRONT.crownY)
        assertEquals(102f, HeadTarget.FEMALE_SIDE.crownY)
        assertEquals(794f, HeadTarget.FEMALE_FRONT.chinY)
        assertEquals(798f, HeadTarget.FEMALE_SIDE.chinY)
    }

    @Test
    fun `턱 중심은 목표 목 자리로, 정수리는 목표 정수리 줄로 간다`() {
        for (target in HeadTarget.entries) {
            val placement = HeadPlacement.fit(centerX = 500f, chinY = 760f, crownY = 160f, target = target)
            assertNear(target.centerX, placement.mapX(500f))
            assertNear(target.chinY, placement.mapY(760f))
            assertNear(target.crownY, placement.mapY(160f))
        }
    }

    @Test
    fun `배율은 0·5~1·8배로 묶는다`() {
        val tiny = HeadPlacement.fit(512f, 900f, 880f, HeadTarget.FEMALE_FRONT)
        assertEquals(1.8f, tiny.scale)
        assertNear(HeadTarget.FEMALE_FRONT.chinY, tiny.mapY(900f))
        val huge = HeadPlacement.fit(512f, 3000f, 0f, HeadTarget.MALE_FRONT)
        assertEquals(.5f, huge.scale)
    }

    @Test
    fun `몸체 목표는 남녀와 정면·측면마다 하나다`() {
        assertEquals(HeadTarget.FEMALE_FRONT, HeadTarget.front(AvatarGender.FEMALE))
        assertEquals(HeadTarget.MALE_FRONT, HeadTarget.front(AvatarGender.MALE))
        assertEquals(HeadTarget.FEMALE_SIDE, HeadTarget.side(AvatarGender.FEMALE))
        assertEquals(HeadTarget.MALE_SIDE, HeadTarget.side(AvatarGender.MALE))
        assertTrue(HeadTarget.entries.all { it.crownY < it.chinY })
    }

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < .01f, "기대 $expected, 실제 $actual")
}
