package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

/** 셀카 머리 읽기 캐시: 내용이 같은 층 목록은 인스턴스가 달라도 읽어 둔 것을 돌려준다. */
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
        val first = cache.getOrLoad(layers(1), load)
        val second = cache.getOrLoad(layers(1), load)
        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun `내용이 다르면 다시 읽는다`() {
        val cache = SelfieHeadArtCache<Any>(3)
        val first = cache.getOrLoad(layers(1), load)
        assertNotSame(first, cache.getOrLoad(layers(2), load))
        assertEquals(2, loads)
    }

    @Test
    fun `가장 오래 안 쓴 것부터 밀어낸다`() {
        val cache = SelfieHeadArtCache<Any>(2)
        cache.getOrLoad(layers(1), load)
        cache.getOrLoad(layers(2), load)
        cache.getOrLoad(layers(1), load)
        cache.getOrLoad(layers(3), load)
        cache.getOrLoad(layers(1), load)
        assertEquals(3, loads)
        cache.getOrLoad(layers(2), load)
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
}
