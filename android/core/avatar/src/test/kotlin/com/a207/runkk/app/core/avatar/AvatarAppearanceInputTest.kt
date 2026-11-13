package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 화면이 넘기는 값(의상 0..6, 체형 3단계, 러닝량 0..100)과 캐시 키·피부톤 행렬. */
class AvatarAppearanceInputTest {
    @Test
    fun `의상은 부위마다 0~6이고 모두 0이면 기본이다`() {
        assertTrue(AvatarOutfit().isDefault)
        assertTrue(!AvatarOutfit(top = 6).isDefault)
        assertFailsWith<IllegalArgumentException> { AvatarOutfit(top = 7) }
        assertFailsWith<IllegalArgumentException> { AvatarOutfit(shoes = -1) }
    }

    @Test
    fun `러닝량 대응 자리는 톤 순번을 0~100으로 고르게 나눈다`() {
        assertEquals(listOf(0, 25, 50, 75, 100), (0..4).map { skinToneAmountFrom(it, toneCount = 5) })
        assertEquals(0, skinToneAmountFrom(0, toneCount = 1))
        assertFailsWith<IllegalArgumentException> { skinToneAmountFrom(5, toneCount = 5) }
        assertFailsWith<IllegalArgumentException> { skinToneAmountFrom(0, toneCount = 0) }
    }

    @Test
    fun `피부톤 행렬은 0이면 그대로이고 100이면 가장 어두운 톤의 60%(RGB 0·82·0·70·0·592배)까지만 줄이며 알파는 그대로다`() {
        val identity = SkinToneFilter.matrix(0)
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf(identity[0], identity[6], identity[12], identity[18]))
        val dark = SkinToneFilter.matrix(100)
        assertEquals(.82f, dark[0], .0001f)
        assertEquals(.70f, dark[6], .0001f)
        assertEquals(.592f, dark[12], .0001f)
        assertEquals(1f, dark[18])
        assertFailsWith<IllegalArgumentException> { SkinToneFilter.matrix(101) }
    }

    @Test
    fun `피부 텍스처 캐시 키는 그 자산을 바꾸는 의상 부위만 담는다`() {
        val outfit = AvatarOutfit(top = 1, bottom = 2, shoes = 3)
        assertEquals(SkinRenderKey(40, 1, 2, 3), SkinRenderKey.of("standing_body", 40, outfit))
        assertEquals(
            SkinRenderKey(40, 1, 2, 3, shape = 2),
            SkinRenderKey.of("standing_body", 40, outfit, AvatarBodyShape.CHANGE_2),
            "체형 전용 파일이 있을 수 있어 체형도 키"
        )
        // 남자 내장 기본 옷 몸은 의상 이전 뒤 그리지 않는다(의상 슬롯 아님).
        assertEquals(SkinRenderKey(40), SkinRenderKey.of(MaleSelectedBody.ASSET, 40, outfit))
        assertEquals(SkinRenderKey(40, top = 1), SkinRenderKey.of("continuous_arm", 40, outfit))
        assertEquals(SkinRenderKey(40, top = 1), SkinRenderKey.of("side_body_cream", 40, outfit))
        assertEquals(SkinRenderKey(40, bottom = 2), SkinRenderKey.of("side_leg_skin", 40, outfit))
        assertEquals(SkinRenderKey(40, shoes = 3), SkinRenderKey.of("side_shoe", 40, outfit))
        assertEquals(SkinRenderKey(40), SkinRenderKey.of("side_head", 40, outfit))
    }
}
