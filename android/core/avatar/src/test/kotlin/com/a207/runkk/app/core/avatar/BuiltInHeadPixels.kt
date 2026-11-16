package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.test.assertEquals
import org.robolectric.RuntimeEnvironment

/** 그림 검사용 원본 픽셀. 파일과 렌더 결과를 바꾸지 않는다. */
internal class BuiltInHeadPixels(val pixels: IntArray) {
    operator fun get(x: Int, y: Int): Int = pixels[y * SIZE + x]

    fun bounds(): Rect {
        val opaque = pixels.indices.filter { pixels[it] ushr 24 >= 128 }
        return Rect(
            opaque.minOf { it % SIZE },
            opaque.minOf { it / SIZE },
            opaque.maxOf { it % SIZE } + 1,
            opaque.maxOf { it / SIZE } + 1
        )
    }

    fun rowCenter(y: Int): Float {
        val xs = (0 until SIZE).filter { get(it, y) ushr 24 >= 128 }
        return (xs.first() + xs.last()) / 2f
    }

    /** 외부와 8방향으로 이어진 투명 영역을 지워 내부 구멍만 남긴다. */
    private fun exterior(): BooleanArray {
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var tail = 0
        fun enqueue(index: Int) {
            if (!visited[index] && pixels[index] ushr 24 == 0) {
                visited[index] = true
                queue[tail++] = index
            }
        }
        for (edge in 0 until SIZE) {
            enqueue(edge)
            enqueue((SIZE - 1) * SIZE + edge)
            enqueue(edge * SIZE)
            enqueue(edge * SIZE + SIZE - 1)
        }
        var head = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % SIZE
            if (x > 0) enqueue(index - 1)
            if (x < SIZE - 1) enqueue(index + 1)
            if (index >= SIZE) {
                enqueue(index - SIZE)
                if (x > 0) enqueue(index - SIZE - 1)
                if (x < SIZE - 1) enqueue(index - SIZE + 1)
            }
            if (index < pixels.size - SIZE) {
                enqueue(index + SIZE)
                if (x > 0) enqueue(index + SIZE - 1)
                if (x < SIZE - 1) enqueue(index + SIZE + 1)
            }
        }
        return visited
    }

    /** 피부의 줄별 윤곽과 그 주위 3px만 검사해 머리카락 바깥 물결 틈을 제외한다. */
    fun internalGaps(skin: BuiltInHeadPixels): List<Pair<Int, Int>> {
        val outside = exterior()
        val gaps = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until SIZE) {
            val xs = (0 until SIZE).filter { skin[it, y] ushr 24 >= 128 }
            if (xs.isEmpty()) continue
            for (x in (xs.first() - 3).coerceAtLeast(0)..(xs.last() + 3).coerceAtMost(SIZE - 1)) {
                val index = y * SIZE + x
                if (pixels[index] ushr 24 == 0 && !outside[index]) gaps += x to y
            }
        }
        return gaps
    }

    fun colors(rect: Rect): List<Int> = buildList {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                if (get(x, y) ushr 24 >= 250) add(get(x, y))
            }
        }
    }.distinct()

    companion object {
        const val SIZE = 1024

        fun read(gender: String, name: String): BuiltInHeadPixels =
            RuntimeEnvironment.getApplication().assets.open("avatars/$gender/$name.png").use {
                val bitmap = checkNotNull(BitmapFactory.decodeStream(it))
                try {
                    from(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }

        fun from(bitmap: Bitmap): BuiltInHeadPixels {
            assertEquals(SIZE, bitmap.width)
            assertEquals(SIZE, bitmap.height)
            return BuiltInHeadPixels(IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) })
        }

        fun decode(bytes: ByteArray): BuiltInHeadPixels {
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                return from(bitmap)
            } finally {
                bitmap.recycle()
            }
        }

        fun transparentPng(): ByteArray {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            try {
                return ByteArrayOutputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        fun composite(vararg layers: BuiltInHeadPixels): BuiltInHeadPixels {
            val result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(result)
                layers.forEach { layer ->
                    val bitmap = Bitmap.createBitmap(layer.pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
                    try {
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    } finally {
                        bitmap.recycle()
                    }
                }
                return from(result)
            } finally {
                result.recycle()
            }
        }

        fun colorDistance(first: Int, second: Int): Int =
            listOf(0, 8, 16).maxOf { abs((first shr it and 255) - (second shr it and 255)) }
    }
}
