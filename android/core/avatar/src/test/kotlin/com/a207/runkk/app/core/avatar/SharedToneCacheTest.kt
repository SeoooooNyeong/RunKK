package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 공용 합성 캐시([임시:D-성-105], [임시:DR-성-10]): 남녀 × 동작마다 마지막 모습 하나(상한 4묶음). 키는 성별·동작·머리 동일성(참조)·
 * 의상·체형·러닝량이다. 뷰는 합성 전에 이 캐시를 먼저 본다.
 */
class SharedToneCacheTest {
    private val cache = SharedToneCache()
    private val look = AvatarLookKey(AvatarOutfit(top = 1), AvatarBodyShape.BASE, 40)

    @Test
    fun `같은 머리·모습이면 맞고, 머리 참조나 모습이 다르면 맞지 않는다`() {
        val head = head()
        cache.put(AvatarGender.FEMALE, AvatarMotion.STAND, head, look, mixed())

        assertNotNull(cache.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), head, look))
        assertNull(cache.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), head(), look), "다른 머리 인스턴스")
        assertNull(cache.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), null, look), "내장 머리")
        assertNull(cache.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), head, look.copy(amount = 41)), "러닝량")
        assertNull(cache.hit(AvatarGender.MALE, setOf(AvatarMotion.STAND), head, look), "성별")
        assertNull(cache.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND, AvatarMotion.RUN), head, look), "달리기 없음")
    }

    @Test
    fun `동작마다 그 동작의 텍스처만 둔다`() {
        cache.put(AvatarGender.MALE, AvatarMotion.STAND, null, look, mixed())
        cache.put(AvatarGender.MALE, AvatarMotion.RUN, null, look, mixed())

        val stand = checkNotNull(cache.entry(AvatarGender.MALE, AvatarMotion.STAND)).cache
        assertEquals(setOf(BodySkinLayers.STANDING_BODY, MaleSelectedBody.ASSET), stand.skinRenders.keys)
        assertEquals(setOf(BodySkinLayers.STANDING_BODY, BodySkinLayers.STANDING_BODY_LEGACY), stand.skinBases.keys)
        assertNotNull(stand.face)
        assertNotNull(stand.frontOverlay)
        assertNull(stand.runOverlay)

        val run = checkNotNull(cache.entry(AvatarGender.MALE, AvatarMotion.RUN)).cache
        assertEquals(setOf("side_head", "side_leg_skin"), run.skinRenders.keys)
        assertEquals(setOf("side_leg_skin"), run.skinBases.keys)
        assertNull(run.face)
        assertNull(run.frontOverlay)
        assertNotNull(run.runOverlay)

        val both = checkNotNull(cache.hit(AvatarGender.MALE, AvatarMotion.entries.toSet(), null, look))
        assertEquals(stand.skinRenders.keys + run.skinRenders.keys, both.skinRenders.keys)
        assertSame(stand.face, both.face)
        assertSame(run.runOverlay, both.runOverlay)
    }

    @Test
    fun `남녀 × 동작 4묶음을 넘지 않고 같은 자리는 마지막 모습으로 바꾼다`() {
        repeat(3) { round ->
            AvatarGender.entries.forEach { gender ->
                AvatarMotion.entries.forEach { motion ->
                    cache.put(gender, motion, null, look.copy(amount = round), mixed())
                }
            }
        }
        assertEquals(4, cache.size)
        assertEquals(2, checkNotNull(cache.entry(AvatarGender.FEMALE, AvatarMotion.RUN)).look.amount)
    }

    @Test
    fun `몸 분리 재사용용 씨앗은 같은 머리의 두 동작을 합치고 다른 머리면 비어 있다`() {
        val head = head()
        cache.put(AvatarGender.FEMALE, AvatarMotion.STAND, head, look, mixed())
        cache.put(AvatarGender.FEMALE, AvatarMotion.RUN, head, look.copy(amount = 90), mixed())

        val seed = cache.seed(AvatarGender.FEMALE, head)
        assertEquals(
            setOf(BodySkinLayers.STANDING_BODY, BodySkinLayers.STANDING_BODY_LEGACY, "side_leg_skin"),
            seed.skinBases.keys
        )
        assertEquals(emptyMap(), cache.seed(AvatarGender.FEMALE, head()).skinBases, "다른 머리")
        assertEquals(emptyMap(), cache.seed(AvatarGender.MALE, head).skinBases, "다른 성별")
    }

    @Test
    fun `비우면 모두 지운다`() {
        cache.put(AvatarGender.FEMALE, AvatarMotion.STAND, null, look, mixed())
        cache.clear()
        assertEquals(0, cache.size)
    }

    private fun head() = GeneratedHeadLayers(emptyMap(), skinColor = 0)

    /** 서기·달리기 텍스처가 섞인 캐시(뷰 하나가 두 동작을 다 가진 경우). */
    private fun mixed(): AvatarToneCache {
        val bytes = byteArrayOf(1)
        val layers = bytes
        fun skin() = AvatarToneCache.RenderedSkin(SkinRenderKey(40), bytes)
        return AvatarToneCache(
            skinBases = mapOf(
                BodySkinLayers.STANDING_BODY to layers,
                BodySkinLayers.STANDING_BODY_LEGACY to layers,
                "side_leg_skin" to layers
            ),
            skinRenders = mapOf(
                BodySkinLayers.STANDING_BODY to skin(),
                MaleSelectedBody.ASSET to skin(),
                "side_head" to skin(),
                "side_leg_skin" to skin()
            ),
            face = AvatarToneCache.RenderedFace(40, bytes, bytes),
            frontOverlay = AvatarToneCache.RenderedOverlay(AvatarBodyShape.BASE, 40, bytes),
            runOverlay = AvatarToneCache.RenderedOverlay(AvatarBodyShape.BASE, 40, bytes)
        )
    }
}
