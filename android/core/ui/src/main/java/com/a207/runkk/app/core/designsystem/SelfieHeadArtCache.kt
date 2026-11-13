package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 층 목록의 내용(부위와 png 바이트, [FaceLayer]의 값 비교)과 읽은 크기 단계(variant, 예: 줄여 읽은 배수)로 찾는 작은 최근 사용 캐시.
 * 못 읽은 결과(null)는 담지 않는다. 담은 개수가 [maxSize]를 넘거나 무게([weigh], 예: 비트맵 바이트) 합이 [maxWeight]를 넘으면 가장
 * 오래 안 쓴 것부터 버린다(방금 담은 하나는 남긴다). 여러 스레드가 함께 써도 된다.
 */
internal class SelfieHeadArtCache<T : Any>(
    private val maxSize: Int,
    private val maxWeight: Long = Long.MAX_VALUE,
    private val weigh: (T) -> Long = { 0L }
) {
    private data class Key(val layers: List<FaceLayer>, val variant: Int)

    private val map = LinkedHashMap<Key, T>(maxSize, LOAD_FACTOR, true)
    private var weight = 0L

    /** 지금 담겨 있는 개수(시험용). */
    val size: Int get() = synchronized(map) { map.size }

    /** 내용과 [variant]가 같으면 담아 둔 것을, 없으면 [load]로 읽어 담고 돌려준다. */
    fun getOrLoad(layers: List<FaceLayer>, variant: Int = 0, load: (List<FaceLayer>) -> T?): T? {
        val sorted = layers.sortedBy { it.part.ordinal }
        val key = Key(sorted, variant)
        synchronized(map) { map[key] }?.let { return it }
        val loaded = load(sorted) ?: return null
        synchronized(map) {
            map.put(key, loaded)?.let { weight -= weigh(it) }
            weight += weigh(loaded)
            trim(key)
        }
        return loaded
    }

    /** 개수·무게 상한을 넘으면 오래 안 쓴 것부터 버린다. [keep](방금 담은 것)은 남긴다. */
    private fun trim(keep: Key) {
        val entries = map.entries.iterator()
        while ((map.size > maxSize || weight > maxWeight) && entries.hasNext()) {
            val eldest = entries.next()
            if (eldest.key == keep) continue
            weight -= weigh(eldest.value)
            entries.remove()
        }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
