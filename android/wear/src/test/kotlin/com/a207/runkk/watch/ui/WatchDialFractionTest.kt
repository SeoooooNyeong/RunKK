package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import kotlin.test.Test
import kotlin.test.assertEquals

/** 거리 고리는 고른 거리를 가장 긴 거리로 나눈 만큼 찬다(피그마 F1-3·F1-5·F1-10·B1-1). */
class WatchDialFractionTest {
    private fun choices(vararg meters: Long?) = meters.map { WatchFlow.Choice(it?.let(::Distance)) }

    @Test
    fun `자유런 3km는 10분의 3, 5km는 절반, 10km는 끝까지 찬다`() {
        val free = choices(null, 3_000, 5_000, 10_000)
        assertEquals(0.3f, dialFraction(WatchFlow.FreeGoal(free, selected = 1)))
        assertEquals(0.5f, dialFraction(WatchFlow.FreeGoal(free, selected = 2)))
        assertEquals(1f, dialFraction(WatchFlow.FreeGoal(free, selected = 3)))
    }

    @Test
    fun `배틀런 1km는 10분의 1, 무제한은 끝까지 찬다`() {
        assertEquals(0.1f, dialFraction(WatchFlow.BattleDistance(choices(1_000, 3_000, 5_000, 10_000))))
        assertEquals(1f, dialFraction(WatchFlow.FreeGoal(choices(null, 3_000, 5_000, 10_000))))
    }
}
