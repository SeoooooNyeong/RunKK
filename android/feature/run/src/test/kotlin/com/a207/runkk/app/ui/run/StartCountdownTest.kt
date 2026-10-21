package com.a207.runkk.app.ui.run

import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartCountdownTest {
    @Test
    fun `3·2·1을 1초마다 세고 끝에 한 번 시작한다(FR-180)`() = runTest {
        val ticks = mutableListOf<Int>()
        var finished = 0
        val countdown = StartCountdown(this)
        countdown.begin(onTick = { ticks += it }, onFinish = { finished++ })
        runCurrent()
        assertEquals(listOf(3), ticks)
        advanceTimeBy(1_001)
        assertEquals(listOf(3, 2), ticks)
        advanceUntilIdle()
        assertEquals(listOf(3, 2, 1), ticks)
        assertEquals(1, finished)
    }

    @Test
    fun `숫자를 누르면 바로 시작하고 남은 숫자는 세지 않는다`() = runTest {
        val ticks = mutableListOf<Int>()
        var finished = 0
        val countdown = StartCountdown(this)
        countdown.begin(onTick = { ticks += it }, onFinish = { finished++ })
        runCurrent()
        countdown.skip()
        advanceUntilIdle()
        assertEquals(listOf(3), ticks)
        assertEquals(1, finished)
        countdown.skip()
        assertEquals(1, finished)
    }

    @Test
    fun `취소하면 시작하지 않는다`() = runTest {
        var finished = 0
        val countdown = StartCountdown(this)
        countdown.begin(onTick = {}, onFinish = { finished++ })
        runCurrent()
        countdown.cancel()
        advanceUntilIdle()
        assertEquals(0, finished)
    }
}
