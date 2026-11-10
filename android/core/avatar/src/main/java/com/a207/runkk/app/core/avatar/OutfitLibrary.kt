package com.a207.runkk.app.core.avatar

import android.content.res.AssetManager
import android.util.LruCache
import java.io.FileNotFoundException

/** 의상 자산 파일 읽기(assets 기준 경로 → 바이트, 없으면 null). 시험은 파일 폴더로 바꿔 끼운다. */
internal fun interface OutfitFiles {
    fun read(path: String): ByteArray?
}

/**
 * 프로세스 공용 의상 자산(assets/outfits). [PreparedRiveAssets.read]가 IO 스레드에서 한 번 [install]하고, 합성 스레드가
 * [catalog]와 [read]를 쓴다. 읽은 PNG 바이트는 상한(약 8MB) 안에서 다시 쓴다.
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

    /** 목록. 설치 전이거나 목록이 없으면 null(그때는 기본 표면·기본 묶음 규칙만 쓴다). */
    val catalog: OutfitCatalog? get() = loaded

    fun install(assets: AssetManager) {
        if (files != null) return
        install(
            OutfitFiles { path ->
                try {
                    assets.open(path).use { it.readBytes() }
                } catch (_: FileNotFoundException) {
                    null
                }
            }
        )
    }

    @Synchronized
    fun install(source: OutfitFiles) {
        files = source
        bytes.evictAll()
        loaded = source.read(OutfitCatalog.INDEX)?.let { OutfitCatalog.parse(it.decodeToString()) }
    }

    /** [path]의 바이트. 파일이 없으면 null. 설치 전이면 IllegalStateException. */
    fun read(path: String): ByteArray? {
        bytes.get(path)?.let { return it }
        val source = checkNotNull(files) { "outfit library not installed" }
        return source.read(path)?.also { bytes.put(path, it) }
    }
}
