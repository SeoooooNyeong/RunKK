package com.a207.runkk.app.core.avatar

/**
 * 시험용 부스러기·속옷 규칙([임시:D-성-184]·[임시:D-성-185], 파이프라인 `outfit_pipeline/body.py`와 같다).
 *
 * 부스러기 = 알파가 있는데 굵은 실루엣(알파 128 이상을 3x3로 깎았다 넓히고(열기) 다시 1px 넓힌 곳) 밖인 픽셀. 실루엣에서 뻗은 1~2px 털과
 * 떨어진 점·먼지다. riv 메시가 팔과 몸통 사이, 엉덩이 옆을 늘리면 선으로 보였다. 그림 가장자리 너머는 가장자리 픽셀과 같다고 본다(PIL 필터와 같다).
 * 속옷 = 서기 허리·엉덩이 칸(x 100~411, y 296~411) 안의 알파 128 이상 무채색(채널 최대−최소 25 미만) 픽셀.
 * 허리 조각 = 하의 서기 레이어의 허리 겹침 띠(y 296~321)에서, 띠 안을 3x3로 연 굵은 픽셀을 4방향으로 이은 덩어리 가운데 하의 본체(y 330 아래까지
 * 이어진 덩어리)에서 1px 넘게 떨어진 알파 픽셀.
 */
internal object StrayPixels {
    private const val SOLID = 128
    private const val NEUTRAL = 25
    private const val ROOT_ROW = 330
    private const val WISP_DARK = 140
    private const val SEAM = 4
    val HIPS = Pair(100 until 412, 296 until 412)

    /** [garment]면 5x5 굵은 실루엣 밖 불투명 갈색 실오라기(R > G + 10, R > B + 20, 밝기 140 미만, 옷 레이어에 남은 맨몸 외곽선 조각)도 센다. */
    fun count(pixels: IntArray, width: Int, height: Int, garment: Boolean = false): Int {
        val solid = BooleanArray(pixels.size) { pixels[it] ushr 24 >= SOLID }
        val keep = grow(grow(shrink(solid, width, height), width, height), width, height)
        val thick = if (garment) {
            val opened5 = grow(grow(shrink(shrink(solid, width, height), width, height), width, height), width, height)
            grow(opened5, width, height)
        } else {
            null
        }
        return pixels.indices.count { i ->
            val p = pixels[i]
            if (p ushr 24 == 0) return@count false
            if (!keep[i]) return@count true
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            thick != null && solid[i] && !thick[i] && r > g + 10 && r > b + 20 && (r + g + b) / 3.0 < WISP_DARK
        }
    }

    fun underwear(pixels: IntArray, width: Int): Int {
        val (xs, ys) = HIPS
        var total = 0
        for (y in ys) {
            for (x in xs) {
                val p = pixels[y * width + x]
                val channels = listOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
                if (p ushr 24 >= SOLID && channels.max() - channels.min() < NEUTRAL) total++
            }
        }
        return total
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
