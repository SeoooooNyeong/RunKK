package com.a207.runkk.app.core.model.avatar

import kotlin.test.Test
import kotlin.test.assertEquals

/** 그을림 상한(PM 2026-11-18): 러닝량 100이어도 가장 어두운 톤의 40%까지. 몸(Rive)과 얼굴 원이 같은 값을 쓴다. */
class SkinToneTest {
    @Test
    fun `상한은 40%이고 러닝량에 비례한다`() {
        assertEquals(.40f, SkinTone.MAX_TAN)
        assertEquals(0f, SkinTone.tanFraction(0))
        assertEquals(.20f, SkinTone.tanFraction(50), 1e-6f)
        assertEquals(.40f, SkinTone.tanFraction(100), 1e-6f)
    }

    @Test
    fun `범위 밖 러닝량은 0~100으로 자른다`() {
        assertEquals(0f, SkinTone.tanFraction(-5))
        assertEquals(.40f, SkinTone.tanFraction(150), 1e-6f)
    }

    @Test
    fun `러닝량 100의 채널 배율은 R 0_88, G 0_80, B 0_728이다`() {
        val scales = SkinTone.channelScales(100)
        assertEquals(.88f, scales[0], 1e-4f)
        assertEquals(.80f, scales[1], 1e-4f)
        assertEquals(.728f, scales[2], 1e-4f)
        assertEquals(listOf(1f, 1f, 1f), SkinTone.channelScales(0).toList())
    }
}
