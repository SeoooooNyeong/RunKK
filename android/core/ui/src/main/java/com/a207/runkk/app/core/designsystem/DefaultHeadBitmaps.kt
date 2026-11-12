package com.a207.runkk.app.core.designsystem

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 기본 머리 그림(512×512 drawable)을 그릴 크기에 맞춰 줄여 읽어 두는 캐시. 그림·줄인 배수(1, 2, 4, 8)로 찾는다. 랭킹·크루 목록의 40dp
 * 얼굴은 128px 안팎으로 읽어(원본 1MB → 64KB) 줄마다 원본을 다시 읽지 않고 같은 비트맵을 함께 쓴다. 여러 스레드가 함께 써도 된다.
 */
internal object DefaultHeadBitmaps {
    private data class Key(val id: Int, val sample: Int)

    private const val MAX_ENTRIES = 16
    private const val MAX_SAMPLE = 8

    private val map = object : LinkedHashMap<Key, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?): Boolean = size > MAX_ENTRIES
    }

    /** 원본 한 변이 [sourceSide]인 그림을 화면 [sidePx]에 그릴 때 줄일 배수. 줄인 그림 1px이 화면 1px을 넘지 않는 가장 큰 2의 거듭제곱. */
    internal fun sampleFor(sourceSide: Int, sidePx: Int): Int {
        if (sourceSide <= 0 || sidePx <= 0) return 1
        var sample = 1
        while (sample < MAX_SAMPLE && sourceSide / (sample * 2) >= sidePx) sample *= 2
        return sample
    }

    /** [id] 그림을 화면 [sidePx]에 그릴 비트맵. 읽지 못하면 null. */
    fun of(resources: Resources, @DrawableRes id: Int, sidePx: Int): ImageBitmap? {
        val sample = sampleFor(sourceSideOf(resources, id), sidePx)
        val key = Key(id, sample)
        synchronized(map) { map[key] }?.let { return it }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            // drawable-nodpi라 밀도 보정 없이 원본 크기로 읽는다.
            inScaled = false
        }
        val image = BitmapFactory.decodeResource(resources, id, options)?.asImageBitmap() ?: return null
        synchronized(map) { map[key] = image }
        return image
    }

    private val sides = HashMap<Int, Int>()

    /** 그림의 원본 한 변(크기만 읽는다, 그림마다 한 번). */
    private fun sourceSideOf(resources: Resources, @DrawableRes id: Int): Int {
        synchronized(sides) { sides[id] }?.let { return it }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, id, options)
        val side = maxOf(options.outWidth, options.outHeight)
        if (side > 0) synchronized(sides) { sides[id] = side }
        return side
    }
}
