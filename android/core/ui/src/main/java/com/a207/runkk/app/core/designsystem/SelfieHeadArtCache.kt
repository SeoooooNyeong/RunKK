package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 층 목록의 내용(부위와 png 바이트, [FaceLayer]의 값 비교)으로 찾는 작은 최근 사용 캐시. 못 읽은 결과(null)는 담지 않는다.
 * 여러 스레드가 함께 써도 된다.
 */
internal class SelfieHeadArtCache<T : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<List<FaceLayer>, T>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<FaceLayer>, T>?): Boolean = size > maxSize
    }

    fun getOrLoad(layers: List<FaceLayer>, load: (List<FaceLayer>) -> T?): T? {
        val key = layers.sortedBy { it.part.ordinal }
        synchronized(map) { map[key] }?.let { return it }
        val loaded = load(key) ?: return null
        synchronized(map) { map[key] = loaded }
        return loaded
    }
}
