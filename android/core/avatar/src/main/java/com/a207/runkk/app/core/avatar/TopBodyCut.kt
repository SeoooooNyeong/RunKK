package com.a207.runkk.app.core.avatar

import kotlin.math.hypot

/**
 * 상의 자르기([임시:D-성-156]): 맨몸 바탕에서 상의 밖으로 비치는 얇은 테두리를 지운다. 맨몸 실루엣보다 좁은 상의가 와도 어깨·몸통·소매 옆
 * 피부 테두리가 옷 밖으로 보이지 않게 하고(러닝량이 높으면 짙은 피부 덩어리로 보였다), 일부러 보이는 맨몸(소매 끝 아래 팔, 민소매의
 * 어깨·팔, 팔과 몸통 사이 안쪽 윤곽, 목, 손)은 남긴다.
 *
 * 규칙: 자르기 칸(알파 128 이상) 안의 상의 밖(옷으로 둘러싸인 빈 곳은 옷으로 친다) 맨몸 픽셀 p가 상의와 배경(맨몸도 상의도 없는 곳)
 * 사이에 낀 띠이면 지운다. 띠 = 가장 가까운 상의까지 거리 a, 가장 가까운 배경까지 거리 b가 a + b ≤ [RIM_PX] + 2이고, 두 방향이 서로
 * 반대(코사인 < [OPPOSITE])인 곳. 소매 끝 바로 아래 팔 윤곽은 상의가 위, 배경이 옆이라 반대가 아니어서 남는다.
 * 자르기 칸의 알파 240은 띠 판정 없이 상의 밖을 모두 지우는 곳(달리기 몸통)이다. 팔 띠(알파 128~239, 값마다 한 띠)는 상의가 그 띠의 맨몸을 [ARM_COVERED]보다 많이 덮을 때(긴소매)만 자른다. 반팔·민소매는
 * 몸통 옆에 붙은 팔도 띠처럼 보이므로 그대로 둔다(팔 안쪽 윤곽 보존).
 */
internal object TopBodyCut {
    /** 테두리로 보는 두께(텍스처 px). 서기 아틀라스에서 맨몸과 옷 실루엣 차이는 실측 최대 약 25px(후드티 어깨 곡선)였다. */
    const val RIM_PX = 30

    private const val CUT_ALPHA = 128
    private const val OPPOSITE = -0.5
    private const val CRUMB_PX = 80
    private const val ARM_COVERED = 0.6
    private const val TORSO_ALPHA = 255
    private const val FULL_CUT_ALPHA = 240

    /** [skin]·[top]·[area]는 같은 크기의 ARGB 픽셀. [skin]에서 지울 픽셀을 0으로 바꾼다. */
    fun apply(skin: IntArray, top: IntArray, area: IntArray, width: Int, height: Int) {
        val size = width * height
        val outside = outsideOf(top, width, height)
        val isTop = BooleanArray(size) { top[it] ushr 24 != 0 }
        val isBackground = BooleanArray(size) { skin[it] ushr 24 == 0 && top[it] ushr 24 == 0 }
        val nearestTop = nearest(isTop, width, height)
        val nearestBackground = nearest(isBackground, width, height)
        val skipped = uncoveredArmBands(skin, top, area)
        for (i in 0 until size) {
            val band = area[i] ushr 24
            if (skin[i] ushr 24 == 0 || band < CUT_ALPHA || band in skipped || !outside[i]) continue
            if (band == FULL_CUT_ALPHA) {
                skin[i] = 0 // 달리기 몸통: 맨몸이 보일 곳이 없어(목 조각은 칸 밖) 상의 밖은 모두 지운다
                continue
            }
            val t = nearestTop[i]
            if (t < 0) continue
            val x = i % width
            val y = i / width
            val tx = (t % width - x).toDouble()
            val ty = (t / width - y).toDouble()
            // 배경: 가장 가까운 빈 픽셀, 또는 텍스처 가장자리 너머(가장자리에 닿은 맨몸은 그 바깥이 배경이다)
            val b = nearestBackground[i]
            var bx = if (b >= 0) (b % width - x).toDouble() else Double.MAX_VALUE
            var by = if (b >= 0) (b / width - y).toDouble() else 0.0
            for ((ex, ey) in listOf(
                -(x + 1.0) to 0.0,
                (width - x).toDouble() to 0.0,
                0.0 to -(y + 1.0),
                0.0 to (height - y).toDouble()
            )) {
                if (hypot(ex, ey) < hypot(bx, by)) {
                    bx = ex
                    by = ey
                }
            }
            val toTop = hypot(tx, ty)
            val toBackground = hypot(bx, by)
            if (toTop + toBackground > RIM_PX + 2) continue
            val cosine = (tx * bx + ty * by) / (toTop * toBackground)
            if (cosine < OPPOSITE) skin[i] = 0
        }
        dropCrumbs(skin, top, area, outside, nearestTop, skipped, width, height)
    }

    /**
     * 띠를 지우고 남은 부스러기(테두리 외곽선의 점, 모서리 조각): 자르기 칸 안 상의 밖 맨몸을 4방향으로 이은 덩어리가 [CRUMB_PX] 픽셀
     * 이하이고 모든 픽셀이 상의에서 [RIM_PX] 안이면 지운다. 팔·몸처럼 큰 덩어리는 그대로다.
     */
    private fun dropCrumbs(
        skin: IntArray,
        top: IntArray,
        area: IntArray,
        outside: BooleanArray,
        nearestTop: IntArray,
        skipped: Set<Int>,
        width: Int,
        height: Int
    ) {
        val size = width * height
        val exposed = BooleanArray(size) { i ->
            val band = area[i] ushr 24
            skin[i] ushr 24 != 0 && top[i] ushr 24 == 0 && band >= CUT_ALPHA && band !in skipped && outside[i]
        }
        val seen = BooleanArray(size)
        val queue = IntArray(size)
        for (start in 0 until size) {
            if (!exposed[start] || seen[start]) continue
            var head = 0
            var tail = 0
            var near = true
            seen[start] = true
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val t = nearestTop[i]
                if (t < 0 || squared(i, t, width) > RIM_PX * RIM_PX) near = false
                val x = i % width
                if (x > 0) tail = visit(i - 1, exposed, seen, queue, tail)
                if (x < width - 1) tail = visit(i + 1, exposed, seen, queue, tail)
                if (i >= width) tail = visit(i - width, exposed, seen, queue, tail)
                if (i < (height - 1) * width) tail = visit(i + width, exposed, seen, queue, tail)
            }
            if (near && tail <= CRUMB_PX) {
                for (k in 0 until tail) skin[queue[k]] = 0
            }
        }
    }

    private fun visit(index: Int, exposed: BooleanArray, seen: BooleanArray, queue: IntArray, tail: Int): Int {
        if (!exposed[index] || seen[index]) return tail
        seen[index] = true
        queue[tail] = index
        return tail + 1
    }

    /** 상의가 맨몸을 [ARM_COVERED] 이하로 덮는 팔 띠(자르지 않는다)의 알파 값. */
    private fun uncoveredArmBands(skin: IntArray, top: IntArray, area: IntArray): Set<Int> {
        val total = mutableMapOf<Int, Int>()
        val covered = mutableMapOf<Int, Int>()
        for (i in area.indices) {
            val band = area[i] ushr 24
            if (band < CUT_ALPHA || band == TORSO_ALPHA || skin[i] ushr 24 == 0) continue
            total[band] = (total[band] ?: 0) + 1
            if (top[i] ushr 24 != 0) covered[band] = (covered[band] ?: 0) + 1
        }
        return total.filter { (band, count) -> (covered[band] ?: 0).toDouble() / count <= ARM_COVERED }.keys
    }

    /** 픽셀마다 [source]에서 가장 가까운 픽셀 번호(8방향 원점 전파, 근사). 없으면 -1. */
    private fun nearest(source: BooleanArray, width: Int, height: Int): IntArray {
        val origin = IntArray(source.size) { -1 }
        val queue = IntArray(source.size)
        var head = 0
        var tail = 0
        for (i in source.indices) {
            if (source[i]) {
                origin[i] = i
                queue[tail++] = i
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            val y = i / width
            val o = origin[i]
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if ((dx == 0 && dy == 0) || nx !in 0 until width || ny !in 0 until height) continue
                    val n = ny * width + nx
                    if (origin[n] < 0) {
                        origin[n] = o
                        queue[tail++] = n
                    } else if (squared(n, o, width) < squared(n, origin[n], width)) {
                        origin[n] = o
                    }
                }
            }
        }
        return origin
    }

    private fun squared(a: Int, b: Int, width: Int): Int {
        val dx = a % width - b % width
        val dy = a / width - b / width
        return dx * dx + dy * dy
    }

    /** 상의 알파 밖이면서 텍스처 가장자리와 이어진 픽셀(옷으로 둘러싸인 빈 곳은 false). */
    fun outsideOf(top: IntArray, width: Int, height: Int): BooleanArray {
        val outside = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0
        fun visit(index: Int) {
            if (!outside[index] && top[index] ushr 24 == 0) {
                outside[index] = true
                queue[tail++] = index
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
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            if (x > 0) visit(index - 1)
            if (x < width - 1) visit(index + 1)
            if (index >= width) visit(index - width)
            if (index < (height - 1) * width) visit(index + width)
        }
        return outside
    }
}
