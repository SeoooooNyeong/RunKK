package com.a207.runkk.watch.alert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ToneGeneratorBeeper: 비프음 장치를 처음 울릴 때 열고, 멈추면 놓고, 열기 실패는 다음 재생 시작 때 다시 시도한다. */
class ToneGeneratorBeeperTest {
    private class FakeTone : ToneGeneratorBeeper.Tone {
        val played = mutableListOf<Int>()
        var released = 0

        override fun play(durationMillis: Int) {
            played += durationMillis
        }

        override fun release() {
            released++
        }
    }

    private class Opener(var failuresLeft: Int = 0) {
        val opened = mutableListOf<FakeTone>()
        var attempts = 0

        fun open(): ToneGeneratorBeeper.Tone {
            attempts++
            if (failuresLeft > 0) {
                failuresLeft--
                throw RuntimeException("오디오 장치를 열 수 없음")
            }
            return FakeTone().also { opened += it }
        }
    }

    @Test
    fun `처음 울릴 때 한 번 열고 이어서 다시 쓴다`() {
        val opener = Opener()
        val beeper = ToneGeneratorBeeper(opener::open)

        beeper.beep(80)
        beeper.beep(80)

        assertEquals(1, opener.attempts)
        assertEquals(listOf(80, 80), opener.opened.single().played)
    }

    @Test
    fun `놓으면 장치를 release하고 다음 비프에서 새로 연다`() {
        val opener = Opener()
        val beeper = ToneGeneratorBeeper(opener::open)
        beeper.beep(80)

        beeper.release()
        beeper.release()
        beeper.beep(80)

        assertEquals(1, opener.opened.first().released)
        assertEquals(2, opener.opened.size)
    }

    @Test
    fun `열기에 실패하면 그 재생 동안은 다시 열지 않고 다음 재생 시작 때 다시 시도한다`() {
        val opener = Opener(failuresLeft = 1)
        val beeper = ToneGeneratorBeeper(opener::open)

        beeper.beep(80)
        beeper.beep(80)
        assertEquals(1, opener.attempts)
        assertTrue(opener.opened.isEmpty())

        beeper.release()
        beeper.beep(80)

        assertEquals(2, opener.attempts)
        assertEquals(listOf(80), opener.opened.single().played)
    }
}
