package com.a207.runkk.app.domain.battle

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 랜덤 대결 배치고사 상수(D-500, J24-12). 폰·워치 모두 랜덤 대결을 잠그지 않는다: 배치 중(UNRANKED) 회원의 랜덤 대결은 배치고사 3판이다
 * (FR-274, 워치 배치고사 S15P31A207-275).
 */
class RandomBattleGateTest {
    @Test
    fun `배치고사 판 수는 3이다(진행 n의 분모)`() {
        assertEquals(3, RandomBattleGate.PLACEMENT_RUNS)
    }

    @Test
    fun `배치 중 티어 이름은 서버 Tier UNRANKED다`() {
        assertEquals("UNRANKED", RandomBattleGate.UNRANKED)
    }
}
