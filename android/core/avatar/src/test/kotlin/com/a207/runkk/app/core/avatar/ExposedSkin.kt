package com.a207.runkk.app.core.avatar

/**
 * 시험용 맨살 규칙([임시:D-성-174], 파이프라인 `outfit_pipeline/skin.py`와 같다). 맨살 픽셀 = 알파 32 이상, R ≥ 170, G ≥ 130, 30 ≤ R−G ≤ 80,
 * G−B ≥ 4, 2(G−B) ≤ (R−G)+4(밝은 분홍빛 살구색. 크림·베이지·코랄 천과 러닝량 100 톤이 걸린 피부는 빠진다). 맨살 픽셀을 8방향으로 이은 덩어리가 빈 곳이나 그림
 * 가장자리에 닿고 4픽셀 이상(외곽선 부스러기 제외)이면 「드러난 맨살」이다. 천 안에 갇힌 피부색 무늬는 세지 않는다.
 */
internal object ExposedSkin {
    private const val ALPHA_MIN = 32
    private const val MIN_PIECE = 4

    fun isSkin(pixel: Int): Boolean {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val rg = r - g
        val gb = g - b
        return pixel ushr 24 >= ALPHA_MIN && r >= 170 && g >= 130 && rg in 30..80 && gb >= 4 && 2 * gb <= rg + 4
    }

    /** 드러난 맨살 픽셀 수. [empty]는 「빈 곳」(기본: 그 픽셀 알파 32 미만). */
    fun count(
        pixels: IntArray,
        width: Int,
        height: Int,
        empty: (Int) -> Boolean = { pixels[it] ushr 24 < ALPHA_MIN }
    ): Int {
        val skin = BooleanArray(pixels.size) { isSkin(pixels[it]) }
        val seen = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var total = 0
        for (start in pixels.indices) {
            if (!skin[start] || seen[start]) continue
            var head = 0
            var tail = 0
            var touches = false
            seen[start] = true
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touches = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val n = ny * width + nx
                        if (dx * dx + dy * dy == 1 && empty(n)) touches = true
                        if (skin[n] && !seen[n]) {
                            seen[n] = true
                            queue[tail++] = n
                        }
                    }
                }
            }
            if (touches && tail >= MIN_PIECE) total += tail
        }
        return total
    }
}
