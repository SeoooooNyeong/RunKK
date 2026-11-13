package com.a207.runkk.app.core.avatar

import android.content.res.AssetManager
import android.util.LruCache
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * 프로세스 공용 의상 자산(assets/outfits). [PreparedRiveAssets.read]가 IO 스레드에서 한 번 [install]하고, 합성 스레드가
 * [catalog]와 [read]를 쓴다. 읽은 WebP 바이트는 상한(약 8MB) 안에서 다시 쓰고, 없는 파일 경로는 기억해 다시 찾지 않는다.
 *
 * 설치는 통째로 한 번에 한 스레드만 한다. 목록(index.json)을 먼저 해석하고, 성공해야 파일 출처 → 목록 순서로 연다. 해석이 실패하면
 * 아무것도 바꾸지 않아(예외는 부르는 쪽으로) 다음 [install]이 다시 시도한다.
 */
internal object OutfitLibrary {
    private const val CACHE_BYTES = 8 * 1024 * 1024

    @Volatile
    private var files: OutfitFiles? = null

    @Volatile
    private var loaded: OutfitCatalog? = null

    private val bytes = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 목록. 설치 전이거나 목록이 없으면 null(그때는 기본 표면·기본 묶음 규칙만 쓴다). */
    val catalog: OutfitCatalog? get() = loaded

    /** 앱 자산으로 한 번 설치한다(이미 설치했으면 그대로). */
    fun install(assets: AssetManager) = installOnce(
        OutfitFiles { path ->
            try {
                assets.open(path).use { it.readBytes() }
            } catch (_: FileNotFoundException) {
                null
            }
        }
    )

    /** 아직 설치하지 않았을 때만 [source]로 설치한다. */
    @Synchronized
    fun installOnce(source: OutfitFiles) {
        if (files == null) install(source)
    }

    /** [source]로 (다시) 설치한다. 목록 해석이 실패하면 IllegalArgumentException이고 이전 상태 그대로다. */
    @Synchronized
    fun install(source: OutfitFiles) {
        val catalog = source.read(OutfitCatalog.INDEX)?.let { OutfitCatalog.parse(it.decodeToString()) }
        bytes.evictAll()
        missing.clear()
        // 목록을 본 스레드는 파일 출처도 보도록 출처를 먼저 연다(둘 다 해석이 끝난 뒤라 실패하면 둘 다 그대로다).
        files = source
        loaded = catalog
    }

    /** 설치 전 상태로 되돌린다(시험용). */
    @Synchronized
    fun reset() {
        files = null
        loaded = null
        bytes.evictAll()
        missing.clear()
    }

    /** [path]의 바이트. 파일이 없으면 null. 설치 전이면 IllegalStateException. */
    fun read(path: String): ByteArray? {
        bytes.get(path)?.let { return it }
        val source = checkNotNull(files) { "outfit library not installed" }
        if (path in missing) return null
        val found = source.read(path)
        if (found == null) missing += path else bytes.put(path, found)
        return found
    }

    /** [path] 파일이 있는가([read]와 같은 캐시를 쓴다). */
    fun exists(path: String): Boolean = read(path) != null
}
