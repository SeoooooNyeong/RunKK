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
 * 자르기 칸의 알파 240은 띠 판정 없이 상의 밖을 모두 지우는 곳(달리기 몸통)이다. 알파 221·181(칼라 옆 칸)은 짝 어깨 띠(220·180)를
 * 상의가 덮을 때만 그렇게 지운다. 팔 띠(알파 128~239, 값마다 한 띠)는 상의가 그 띠의 맨몸을 [ARM_COVERED]보다 많이 덮을 때(긴소매)만 자른다. 반팔·민소매는
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
    private const val SLIT_NEIGHBORS = 2
    private const val HIP_ALPHA = 230
    private const val LEG_GUARD = 14

    /**
     * 칼라 옆 칸(알파 → 짝 어깨 띠 알파): 짝 어깨 띠를 상의가 [ARM_COVERED]보다 많이 덮을 때만 띠 판정 없이 상의 밖을 모두 지운다. 칼라
     * 양옆 어깨 윗선은 비스듬한 가는 선이라 띠 판정에 걸리지 않고 짙은 획으로 떴다(판정 round1~3 B). 민소매 맨어깨는 그대로다. cut.py와 같다.
     */
    private val NECK_SIDES = mapOf(221 to 220, 181 to 180)

    /**
     * 자르기 표지 알파. 상의 레이어에서 알파가 정확히 이 값인 픽셀은 옷이 아니라 「이 상의를 입으면 이 자리 맨몸을 지운다」는 세트별 표시다
     * (판정 round6: lime_singlet 밑단 양옆 짙은 획, 파이프라인 `stand_fix.mark_body_cut`). 상의 레이어의 옷 픽셀 알파는 8 이상이다. cut.py와 같다.
     */
    const val MARK_ALPHA = 1

    /** [top]에서 자르기 표지 픽셀을 떼어 내(투명으로) 그 칸을 돌려준다. cut.py `take_marks`와 같다. */
    fun takeMarks(top: IntArray): BooleanArray {
        val marks = BooleanArray(top.size) { top[it] ushr 24 == MARK_ALPHA }
        for (i in top.indices) if (marks[i]) top[i] = 0
        return marks
    }

    /** [marks] 칸의 맨몸을 지운다. cut.py `cut_marks`와 같다. */
    fun cutMarks(skin: IntArray, marks: BooleanArray) {
        for (i in skin.indices) if (marks[i]) skin[i] = 0
    }

    /** [skin]·[top]·[area]는 같은 크기의 ARGB 픽셀. [skin]에서 지울 픽셀을 0으로 바꾼다. */
    fun apply(skin: IntArray, top: IntArray, area: IntArray, width: Int, height: Int) {
        val size = width * height
        val outside = outsideOf(top, width, height)
        val isTop = BooleanArray(size) { top[it] ushr 24 != 0 }
        val isBackground = BooleanArray(size) { skin[it] ushr 24 == 0 && top[it] ushr 24 == 0 }
        val nearestTop = nearest(isTop, width, height)
        val nearestBackground = nearest(isBackground, width, height)
        val skipped = uncoveredArmBands(skin, top, area)
        val before = skin.copyOf()
        val span = rowSpans(isTop, width, height)
        val leg = legSpan(skin, area, width, height)
        for (i in 0 until size) {
            val band = area[i] ushr 24
            if (skin[i] ushr 24 == 0 || band < CUT_ALPHA || band in skipped || !outside[i]) continue
            // 엉덩이 옆(230): 옷(상의·하의)이 그 행에 걸친 가로 범위 밖, 그리고 칸 바로 아래 행 다리의 가로 범위 밖만 지운다(옛 반바지
            // 실루엣 테두리). 하의 밑단 옆 트임 아래와 다리 윗부분은 남긴다
            if (band == HIP_ALPHA) {
                val x = i % width
                val y = i / width
                val row = span[y]
                val legTop = leg != null && y >= leg.second && x in leg.first
                if (row != null && x !in row && !legTop) skin[i] = 0
                continue
            }
            // 달리기 몸통(240): 맨몸이 보일 곳이 없어(목 조각은 칸 밖) 상의 밖은 모두 지운다. 어깨를 덮는 상의의 칼라 옆 칸도 같다
            if (band == FULL_CUT_ALPHA || band in NECK_SIDES ||
                isRim(i, nearestTop[i], nearestBackground[i], width, height)
            ) {
                skin[i] = 0
            }
        }
        closeSlits(before, skin, width, height)
        restoreEnclosed(before, skin, top, width, height)
        dropCrumbs(skin, top, area, outside, nearestTop, skipped, width, height)
    }

    /**
     * 지운 맨몸 가운데 상의 밖이면서 배경(맨몸도 상의도 없는 곳, 텍스처 가장자리 너머 포함)에 8방향으로 닿지 않는 덩어리를 되살린다.
     * 자르기는 상의와 배경 사이 테두리를 지우는 것이라, 배경과 떨어진 곳을 지우면 맨몸 안에 배경이 비치는 구멍만 생겼다(판정 round4: crew 소매
     * 끝 아래 팔의 비스듬한 틈). 파이프라인 `cut.restore_enclosed`와 같다.
     */
    fun restoreEnclosed(before: IntArray, skin: IntArray, top: IntArray, width: Int, height: Int) {
        val size = width * height
        val removed = BooleanArray(size) { before[it] ushr 24 != 0 && skin[it] ushr 24 == 0 && top[it] ushr 24 == 0 }
        fun background(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return true
            val i = y * width + x
            return before[i] ushr 24 == 0 && top[i] ushr 24 == 0
        }
        val seen = BooleanArray(size)
        val queue = IntArray(size)
        for (start in 0 until size) {
            if (!removed[start] || seen[start]) continue
            var head = 0
            var tail = 0
            seen[start] = true
            queue[tail++] = start
            var touches = false
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (background(nx, ny)) touches = true
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val n = ny * width + nx
                        if (removed[n] && !seen[n]) {
                            seen[n] = true
                            queue[tail++] = n
                        }
                    }
                }
            }
            if (!touches) {
                for (k in 0 until tail) skin[queue[k]] = before[queue[k]]
            }
        }
    }

    /**
     * 띠 판정은 가까운 픽셀을 근사로 찾아 겨드랑이·소매 옆처럼 상의 가장자리와 배경이 비스듬히 만나는 곳에서 한 줄 걸러 지워 빗살을 남겼다.
     * 메시가 늘면 점선으로 보였다([임시:D-성-184]). 먼저 지운 줄 사이에 낀 1px 맨몸 줄(위·아래 또는 왼쪽·오른쪽이 모두 지운 픽셀)을 지워 넓은
     * 띠는 고르게 비우고, 그다음 남은 맨몸 사이에 낀 1px 틈 가운데 8방향 이웃에 지운 픽셀이 2개 이하인 것(맨몸 안의 줄 틈)을 되살린다.
     * 파이프라인 `cut.close_slits`와 같다.
     */
    fun closeSlits(before: IntArray, skin: IntArray, width: Int, height: Int) {
        fun removed(i: Int) = before[i] ushr 24 != 0 && skin[i] ushr 24 == 0
        fun kept(i: Int) = skin[i] ushr 24 != 0
        fun sandwiched(i: Int, test: (Int) -> Boolean): Boolean {
            val x = i % width
            val y = i / width
            val vertical = y > 0 && y < height - 1 && test(i - width) && test(i + width)
            val horizontal = x > 0 && x < width - 1 && test(i - 1) && test(i + 1)
            return vertical || horizontal
        }
        skin.indices.filter { kept(it) && sandwiched(it, ::removed) }.forEach { skin[it] = 0 }
        val restore = skin.indices.filter { i ->
            if (!removed(i) || !sandwiched(i, ::kept)) return@filter false
            val x = i % width
            val y = i / width
            var around = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if ((dx != 0 || dy != 0) && nx in 0 until width && ny in 0 until height &&
                        removed(ny * width + nx)
                    ) {
                        around++
                    }
                }
            }
            around <= SLIT_NEIGHBORS
        }
        restore.forEach { skin[it] = before[it] }
    }

    /**
     * [i]가 상의([t])와 배경([b], 없으면 -1) 사이에 낀 띠인가. 텍스처 가장자리 너머도 배경이다(가장자리에 닿은 맨몸은 그 바깥이
     * 배경이다). 띠 = 거리 합 ≤ [RIM_PX] + 2, 두 방향 코사인 < [OPPOSITE].
     */
    private fun isRim(i: Int, t: Int, b: Int, width: Int, height: Int): Boolean {
        if (t < 0) return false
        val x = i % width
        val y = i / width
        val tx = (t % width - x).toDouble()
        val ty = (t / width - y).toDouble()
        // 가장 가까운 배경: 빈 픽셀과 네 가장자리 너머 가운데 가까운 것(할당 없이 차례로 견준다)
        var bx = if (b >= 0) (b % width - x).toDouble() else Double.MAX_VALUE
        var by = if (b >= 0) (b / width - y).toDouble() else 0.0
        var best = hypot(bx, by)
        val left = x + 1.0
        val right = (width - x).toDouble()
        val up = y + 1.0
        val down = (height - y).toDouble()
        if (left < best) {
            best = left
            bx = -left
            by = 0.0
        }
        if (right < best) {
            best = right
            bx = right
            by = 0.0
        }
        if (up < best) {
            best = up
            bx = 0.0
            by = -up
        }
        if (down < best) {
            best = down
            bx = 0.0
            by = down
        }
        val toTop = hypot(tx, ty)
        if (toTop + best > RIM_PX + 2) return false
        return (tx * bx + ty * by) / (toTop * best) < OPPOSITE
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
            skin[i] ushr 24 != 0 && top[i] ushr 24 == 0 && band >= CUT_ALPHA && band != HIP_ALPHA && band !in skipped &&
                outside[i]
        }
        val seen = BooleanArray(size)
        val queue = IntArray(size)
        for (start in 0 until size) {
            if (!exposed[start] || seen[start]) continue
            val count = fill(start, exposed, seen, queue, width, height)
            val near = (0 until count).all { k ->
                val t = nearestTop[queue[k]]
                t >= 0 && squared(queue[k], t, width) <= RIM_PX * RIM_PX
            }
            if (near && count <= CRUMB_PX) {
                for (k in 0 until count) skin[queue[k]] = 0
            }
        }
    }

    /** [start]에서 [exposed]를 4방향으로 이은 덩어리를 [queue] 앞에 채우고 그 픽셀 수를 돌려준다. */
    private fun fill(
        start: Int,
        exposed: BooleanArray,
        seen: BooleanArray,
        queue: IntArray,
        width: Int,
        height: Int
    ): Int {
        var head = 0
        var tail = 0
        seen[start] = true
        queue[tail++] = start
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            if (x > 0) tail = visit(i - 1, exposed, seen, queue, tail)
            if (x < width - 1) tail = visit(i + 1, exposed, seen, queue, tail)
            if (i >= width) tail = visit(i - width, exposed, seen, queue, tail)
            if (i < (height - 1) * width) tail = visit(i + width, exposed, seen, queue, tail)
        }
        return tail
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
            // 몸통(255)과 모두 지우는 칸(240), 칼라 옆 칸(짝 어깨 띠로 본다)은 팔 띠가 아니다(cut.py와 같다)
            if (band < CUT_ALPHA || band == TORSO_ALPHA || band == FULL_CUT_ALPHA || band == HIP_ALPHA ||
                band in NECK_SIDES || skin[i] ushr 24 == 0
            ) {
                continue
            }
            total[band] = (total[band] ?: 0) + 1
            if (top[i] ushr 24 != 0) covered[band] = (covered[band] ?: 0) + 1
        }
        fun uncovered(band: Int): Boolean {
            val count = total[band] ?: return true
            return (covered[band] ?: 0).toDouble() / count <= ARM_COVERED
        }
        return total.keys.filter(::uncovered).toSet() + NECK_SIDES.filterValues(::uncovered).keys
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
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if ((dx == 0 && dy == 0) || nx !in 0 until width || ny !in 0 until height) continue
                    if (spread(ny * width + nx, origin[i], origin, width)) queue[tail++] = ny * width + nx
                }
            }
        }
        return origin
    }

    /** 이웃 [n]에 원점 [o]를 넘긴다. 처음 닿았으면 true(큐에 넣는다), 이미 있으면 더 가까울 때만 바꾼다. */
    private fun spread(n: Int, o: Int, origin: IntArray, width: Int): Boolean {
        if (origin[n] < 0) {
            origin[n] = o
            return true
        }
        if (squared(n, o, width) < squared(n, origin[n], width)) origin[n] = o
        return false
    }

    private fun squared(a: Int, b: Int, width: Int): Int {
        val dx = a % width - b % width
        val dy = a / width - b / width
        return dx * dx + dy * dy
    }

    /**
     * 엉덩이 옆 칸 바로 아래 행에서 맨몸(다리)의 맨 왼쪽~맨 오른쪽과, 그 범위를 지키는 첫 행(칸 맨 아래 [LEG_GUARD]행만: 그 위 허리·엉덩이
     * 옆 옛 실루엣 외곽선은 지운다. 칸이나 맨몸이 없으면 null).
     */
    private fun legSpan(skin: IntArray, area: IntArray, width: Int, height: Int): Pair<IntRange, Int>? {
        val last = (height - 1 downTo 0).firstOrNull { y ->
            (0 until width).any {
                area[y * width + it] ushr 24 ==
                    HIP_ALPHA
            }
        }
        val y = (last ?: return null) + 1
        if (y >= height) return null
        val xs = (0 until width).filter { skin[y * width + it] ushr 24 != 0 }
        return if (xs.isEmpty()) null else (xs.first()..xs.last()) to (y - LEG_GUARD)
    }

    /** 행마다 옷 픽셀의 맨 왼쪽~맨 오른쪽(없으면 null). */
    private fun rowSpans(isTop: BooleanArray, width: Int, height: Int): Array<IntRange?> = Array(height) { y ->
        val row = y * width
        val first = (0 until width).firstOrNull { isTop[row + it] }
        first?.let { it..(width - 1 downTo 0).first { x -> isTop[row + x] } }
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
