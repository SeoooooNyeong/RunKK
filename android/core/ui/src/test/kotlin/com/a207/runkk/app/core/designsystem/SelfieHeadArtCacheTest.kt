package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

/**
 * 셀카 머리 읽기 캐시: 내용이 같은 층 목록은 인스턴스가 달라도 읽어 둔 것을 돌려준다. 읽은 크기 단계(줄인 배수)가 다르면 따로 담고,
 * 무게(비트맵 바이트) 합 상한을 넘으면 오래 안 쓴 것부터 버린다.
 */
class SelfieHeadArtCacheTest {
    private var loads = 0

    private fun layers(seed: Byte, vararg parts: FacePart = arrayOf(FacePart.HAIR, FacePart.EARS)) =
        parts.map { FaceLayer(it, byteArrayOf(seed, 1, 2)) }

    private val load: (List<FaceLayer>) -> Any? = {
        loads++
        Any()
    }

    @Test
    fun `내용이 같고 목록 인스턴스만 다르면 읽어 둔 같은 객체를 돌려준다`() {
        val cache = SelfieHeadArtCache<Any>(3)
        val first = cache.getOrLoad(layers(1), load = load)
        val second = cache.getOrLoad(layers(1), load = load)
        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun `내용이 다르면 다시 읽는다`() {
        val cache = SelfieHeadArtCache<Any>(3)
        val first = cache.getOrLoad(layers(1), load = load)
        assertNotSame(first, cache.getOrLoad(layers(2), load = load))
        assertEquals(2, loads)
    }

    @Test
    fun `가장 오래 안 쓴 것부터 밀어낸다`() {
        val cache = SelfieHeadArtCache<Any>(2)
        cache.getOrLoad(layers(1), load = load)
        cache.getOrLoad(layers(2), load = load)
        cache.getOrLoad(layers(1), load = load)
        cache.getOrLoad(layers(3), load = load)
        cache.getOrLoad(layers(1), load = load)
        assertEquals(3, loads)
        cache.getOrLoad(layers(2), load = load)
        assertEquals(4, loads)
    }

    @Test
    fun `못 읽은 결과는 담지 않아 다음에 다시 시도한다`() {
        val cache = SelfieHeadArtCache<Any>(2)
        assertNull(
            cache.getOrLoad(layers(1)) {
                loads++
                null
            }
        )
        assertNull(
            cache.getOrLoad(layers(1)) {
                loads++
                null
            }
        )
        assertEquals(2, loads)
    }

    @Test
    fun `같은 내용이어도 크기 단계가 다르면 따로 읽어 담는다`() {
        val cache = SelfieHeadArtCache<Any>(4)
        val small = cache.getOrLoad(layers(1), variant = 4, load = load)
        val large = cache.getOrLoad(layers(1), variant = 1, load = load)
        assertNotSame(small, large)
        assertSame(small, cache.getOrLoad(layers(1), variant = 4, load = load))
        assertEquals(2, loads)
    }

    @Test
    fun `무게 합이 상한을 넘으면 오래 안 쓴 것부터 버리고 방금 담은 것은 남긴다`() {
        val cache = SelfieHeadArtCache<Int>(10, maxWeight = 10) { it.toLong() }
        cache.getOrLoad(layers(1), load = { 6 })
        cache.getOrLoad(layers(2), load = { 4 })
        assertEquals(2, cache.size)
        cache.getOrLoad(layers(3), load = { 5 })
        assertEquals(2, cache.size, "6을 버려 4 + 5")
        cache.getOrLoad(layers(4), load = { 20 })
        assertEquals(1, cache.size, "상한보다 무거워도 방금 담은 하나는 남긴다")
        assertEquals(20, cache.getOrLoad(layers(4), load = { 0 }))
    }
}
