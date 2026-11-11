package com.a207.runkk.app.core.avatar

/**
 * 시험용 부스러기·속옷·외곽선 규칙([임시:D-성-184]·[임시:D-성-185], 파이프라인 `outfit_pipeline/body.py`와 같다).
 *
 * 부스러기 = 고립 점: 알파가 있는 픽셀을 8방향으로 이은 덩어리 가운데 40픽셀보다 작거나 알파 128 이상이 하나도 없는 것(떨어진 점, 거의 투명한
 * 먼지). 외곽선에 붙은 픽셀은 몸 덩어리에 이어져 있어 세지 않는다(검수: 외곽선을 깎으면 실기기에서 팔이 울퉁불퉁했다). 옷 레이어는 5x5 굵은
 * 실루엣 밖 불투명 갈색 실오라기도 센다. 그림 가장자리 너머는 가장자리 픽셀과 같다고 본다(PIL 필터와 같다).
 * 속옷 = 서기 허리·엉덩이 칸(x 100~411, y 296~411) 안, 그림 밖과 이어진 빈 곳(알파 16 미만)에서 5px 넘게 떨어진 알파 128 이상 픽셀 가운데
 * 무채색(채널 최대−최소 25 미만)이거나 짙은(밝기 120 미만) 것. 가장자리 5px(맨몸 외곽선)은 원래 그대로라 세지 않는다.
 * 허리 조각 = 하의 서기 레이어의 허리 겹침 띠(y 296~321)에서, 띠 안을 3x3로 연 굵은 픽셀을 4방향으로 이은 덩어리 가운데 하의 본체(y 330 아래까지
 * 이어진 덩어리)에서 1px 넘게 떨어진 알파 픽셀.
 */
internal object StrayPixels {
    private const val SOLID = 128
    private const val NEUTRAL = 25
    private const val ROOT_ROW = 330
    private const val WISP_DARK = 140
    private const val SEAM = 4
    private const val DUST_PIECE = 40
    private const val EMPTY = 16
    private const val EDGE_PX = 5
    private const val DARK = 120
    val HIPS = Pair(100 until 412, 296 until 412)

    /** 부스러기 수. [thin]이면 3x3 굵은 실루엣 밖 털도, [garment]면 5x5 굵은 실루엣 밖 불투명 갈색 실오라기(R > G + 10, R > B + 20, 밝기 140 미만)도 센다. */
    fun count(pixels: IntArray, width: Int, height: Int, garment: Boolean = false, thin: Boolean = false): Int {
        val dust = dust(pixels, width, height)
        val solid = BooleanArray(pixels.size) { pixels[it] ushr 24 >= SOLID }
        // 옷 레이어([thin])는 3x3 굵은 실루엣 밖 1~2px 털도 센다(맨몸은 외곽선을 건드리지 않아 고립 점만)
        val keep = if (thin) grow(grow(shrink(solid, width, height), width, height), width, height) else null
        val thick = if (garment) {
            grow(
                grow(grow(shrink(shrink(solid, width, height), width, height), width, height), width, height),
                width,
                height
            )
        } else {
            null
        }
        return pixels.indices.count { i ->
            val p = pixels[i]
            if (dust[i] || (keep != null && p ushr 24 != 0 && !keep[i])) return@count true
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            thick != null && solid[i] && !thick[i] && r > g + 10 && r > b + 20 && (r + g + b) / 3.0 < WISP_DARK
        }
    }

    /** 굵은 맨몸 실루엣 안(알파 128 이상이면서 3x3로 열고 1px 넓힌 곳 안, 파이프라인 body._body_inside와 같다). */
    fun bodyInside(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val solid = BooleanArray(pixels.size) { pixels[it] ushr 24 >= SOLID }
        val keep = grow(grow(shrink(solid, width, height), width, height), width, height)
        return BooleanArray(pixels.size) { solid[it] && keep[it] }
    }

    /** 고립 점(8방향 덩어리가 40픽셀보다 작거나 알파 128 이상이 없는 것). */
    fun dust(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(pixels.size)
        val seen = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        for (start in pixels.indices) {
            if (pixels[start] ushr 24 == 0 || seen[start]) continue
            var head = 0
            var tail = 0
            var strongest = 0
            seen[start] = true
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                strongest = maxOf(strongest, pixels[i] ushr 24)
                val x = i % width
                val y = i / width
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val n = ny * width + nx
                        if (pixels[n] ushr 24 != 0 && !seen[n]) {
                            seen[n] = true
                            queue[tail++] = n
                        }
                    }
                }
            }
            if (tail < DUST_PIECE || strongest < SOLID) for (k in 0 until tail) out[queue[k]] = true
        }
        return out
    }

    fun underwear(pixels: IntArray, width: Int, height: Int): Int {
        val near = nearOutside(pixels, width, height)
        val (xs, ys) = HIPS
        var total = 0
        for (y in ys) {
            for (x in xs) {
                val i = y * width + x
                val p = pixels[i]
                val channels = listOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
                val neutral = channels.max() - channels.min() < NEUTRAL
                val dark = channels.sum() / 3.0 < DARK
                if (p ushr 24 >= SOLID && (neutral || dark) && !near[i]) total++
            }
        }
        return total
    }

    /** [reference](맨몸 외곽선 기준, 정리 전 원본) 가운데 지금 비었거나(알파 128 미만) 밝아진(밝기 140 이상) 픽셀 수. */
    fun outlineBreaks(pixels: IntArray, reference: BooleanArray): Int = pixels.indices.count { i ->
        if (!reference[i]) return@count false
        val p = pixels[i]
        val mean = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3.0
        p ushr 24 < SOLID || mean >= 140
    }

    /** 그림 밖과 이어진 빈 곳(알파 16 미만, 4방향)에서 [EDGE_PX] 안. */
    private fun nearOutside(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val empty = BooleanArray(pixels.size) { pixels[it] ushr 24 < EMPTY }
        val outside = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var tail = 0
        fun visit(i: Int) {
            if (empty[i] && !outside[i]) {
                outside[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until width) {
            visit(x)
            visit((height - 1) * width + x)
        }
        for (y in 0 until height) {
            visit(y * width)
            visit(y * width + width - 1)
        }
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            if (x > 0) visit(i - 1)
            if (x < width - 1) visit(i + 1)
            if (i >= width) visit(i - width)
            if (i < (height - 1) * width) visit(i + width)
        }
        var near = outside
        repeat(EDGE_PX) { near = grow(near, width, height) }
        return near
    }

    fun looseWaist(pixels: IntArray, width: Int, height: Int): Int {
        val band = 296 until 322
        val solid = BooleanArray(pixels.size) { pixels[it] ushr 24 >= SOLID }
        val opened = grow(shrink(solid, width, height), width, height)
        val kept = BooleanArray(pixels.size) { solid[it] && (opened[it] || it / width !in band) }
        val rooted = BooleanArray(pixels.size)
        val seen = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        for (start in pixels.indices) {
            if (!kept[start] || seen[start]) continue
            var head = 0
            var tail = 0
            var deepest = 0
            seen[start] = true
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                deepest = maxOf(deepest, i / width)
                val x = i % width
                for (n in intArrayOf(
                    if (x >
                        0
                    ) {
                        i - 1
                    } else {
                        -1
                    },
                    if (x < width - 1) i + 1 else -1,
                    i - width,
                    i + width
                )) {
                    if (n in pixels.indices && kept[n] && !seen[n]) {
                        seen[n] = true
                        queue[tail++] = n
                    }
                }
            }
            if (deepest >= ROOT_ROW) for (k in 0 until tail) rooted[queue[k]] = true
        }
        val near = grow(rooted, width, height)
        return pixels.indices.count { it / width in band && pixels[it] ushr 24 != 0 && !near[it] }
    }

    /**
     * 하의 레이어의 세로 4px 이하 틈: 열마다 불투명 천(알파 128 이상) 사이에 낀 줄. 메워도 3x3 굵은 실루엣 밖(가장자리 홈)인 곳은 빼고
     * 센다(파이프라인 body.seam_mask와 같다).
     */
    fun seams(pixels: IntArray, width: Int, height: Int): Int {
        val solid = BooleanArray(pixels.size) { pixels[it] ushr 24 >= SOLID }
        val mask = BooleanArray(pixels.size)
        for (x in 0 until width) {
            var y = 1
            while (y < height - 1) {
                if (solid[y * width + x] || !solid[(y - 1) * width + x]) {
                    y++
                    continue
                }
                var end = y
                while (end < height && !solid[end * width + x]) end++
                if (end < height && end - y <= SEAM) for (k in y until end) mask[k * width + x] = true
                y = end
            }
        }
        val filled = BooleanArray(pixels.size) { solid[it] || mask[it] }
        val thick = grow(grow(shrink(filled, width, height), width, height), width, height)
        return pixels.indices.count { mask[it] && thick[it] }
    }

    private fun shrink(mask: BooleanArray, width: Int, height: Int) = window(mask, width, height, all = true)

    private fun grow(mask: BooleanArray, width: Int, height: Int) = window(mask, width, height, all = false)

    /** 3x3 창(가장자리 너머는 가장자리 픽셀): [all]이면 모두 참일 때, 아니면 하나라도 참일 때 참. */
    private fun window(mask: BooleanArray, width: Int, height: Int, all: Boolean): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var result = all
                loop@ for (dy in -1..1) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val v = mask[ny * width + (x + dx).coerceIn(0, width - 1)]
                        if (v != all) {
                            result = v
                            break@loop
                        }
                    }
                }
                out[y * width + x] = result
            }
        }
        return out
    }
}
