package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/** 입력 글자 수(D-143): 코드 포인트로 세고 서로게이트 쌍을 가르지 않는다. */
class RunkkTextLengthTest {
    @Test
    fun `이모지는 한 자로 센다`() {
        assertEquals(1, EMOJI.codePointLength())
        assertEquals(3, "가${EMOJI}a".codePointLength())
    }

    @Test
    fun `상한까지 자르되 이모지를 반으로 가르지 않는다`() {
        assertEquals(EMOJI.repeat(2), EMOJI.repeat(3).takeCodePoints(2))
        assertEquals("가나", "가나".takeCodePoints(5), "짧으면 그대로")
        assertEquals("", EMOJI.takeCodePoints(0))
    }

    private companion object {
        const val EMOJI = "\uD83C\uDFC3"
    }
}
