package com.a207.runkk.app.core.avatar

/**
 * 헤어 레이어의 피부 섞인 경계(실기기 결함 DV-5). 머리 레이어는 피부 얼굴과 겹치지 않게 나뉘어 있고, 이마·눈썹 둘레의 가는
 * 머리카락 픽셀에는 원본 그림의 밝은 피부색이 섞여 있다. 피부만 어두워지면 그 픽셀이 밝은 선으로 남는다.
 *
 * 디자인 규칙(헤어에 피부톤 필터를 쓰지 않음)을 지키려고 헤어 고유색은 건드리지 않는다. 불투명 얼굴에서 [RADIUS]px 안의 헤어
 * 픽셀만 보고, 그 색을 헤어 대표색 H(불투명 헤어의 채널별 중앙값)와 가장 가까운 불투명 얼굴 픽셀의 피부색 S를 잇는 선에 비춰 피부가
 * 섞인 몫 t(0..1)를 구한 뒤 그 몫의 피부에만 톤을 준다: c' = c + t·(M·S − S). 알파는 그대로다(프리멀티플라이드가 아닌 ARGB).
 */
internal object HairSkinFringe {
    const val RADIUS = 4
    private const val OPAQUE = 200
    private const val CHANNELS = 3
    private const val LEVELS = 256
    private const val MAX = 255
    private const val ALPHA_MASK = -0x1000000

    /**
     * [hair]·[face]는 같은 크기(width×height)의 ARGB 픽셀이다. [matrix]는 [SkinToneFilter.matrix]의 4×5 행렬이고 대각(R·G·B 곱)만
     * 쓴다. 바꾼 헤어 픽셀 사본을 돌려준다.
     */
    fun tone(hair: IntArray, face: IntArray, width: Int, height: Int, matrix: FloatArray): IntArray {
        require(hair.size == width * height && face.size == hair.size) { "hair and face layers differ in size" }
        val out = hair.copyOf()
        val core = hairCore(hair) ?: return out
        val scale = floatArrayOf(matrix[0], matrix[6], matrix[12])
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val c = hair[i]
                if (c ushr 24 == 0) continue
                val skin = nearestOpaqueFace(face, width, height, x, y) ?: continue
                out[i] = toned(c, core, skin, scale)
            }
        }
        return out
    }

    /** 피부가 섞인 몫만큼 피부 톤을 준 색. 헤어 대표색과 피부색이 거의 같으면 그대로. */
    private fun toned(c: Int, core: IntArray, skin: Int, scale: FloatArray): Int {
        val s = intArrayOf(skin shr 16 and MAX, skin shr 8 and MAX, skin and MAX)
        val v = intArrayOf(c shr 16 and MAX, c shr 8 and MAX, c and MAX)
        var dot = 0f
        var length = 0f
        for (k in 0 until CHANNELS) {
            val d = (s[k] - core[k]).toFloat()
            dot += (v[k] - core[k]) * d
            length += d * d
        }
        if (length < 1f) return c
        val t = (dot / length).coerceIn(0f, 1f)
        if (t == 0f) return c
        val r = IntArray(CHANNELS) { k -> (v[k] + t * (scale[k] * s[k] - s[k])).toInt().coerceIn(0, MAX) }
        return (c and ALPHA_MASK) or (r[0] shl 16) or (r[1] shl 8) or r[2]
    }

    /** ([x], [y])에서 [RADIUS]px 안의 가장 가까운 불투명 얼굴 픽셀. 없으면 null. */
    private fun nearestOpaqueFace(face: IntArray, width: Int, height: Int, x: Int, y: Int): Int? {
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (dy in -RADIUS..RADIUS) {
            val ny = y + dy
            if (ny !in 0 until height) continue
            for (dx in -RADIUS..RADIUS) {
                val nx = x + dx
                val distance = dx * dx + dy * dy
                if (nx !in 0 until width || distance > RADIUS * RADIUS || distance >= bestDistance) continue
                val f = face[ny * width + nx]
                if (f ushr 24 > OPAQUE) {
                    best = f
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** 불투명 헤어 픽셀의 채널별 중앙값(헤어 대표색). 불투명 헤어가 없으면 null. */
    private fun hairCore(hair: IntArray): IntArray? {
        val histograms = Array(CHANNELS) { IntArray(LEVELS) }
        var count = 0
        for (c in hair) {
            if (c ushr 24 <= OPAQUE) continue
            histograms[0][c shr 16 and MAX]++
            histograms[1][c shr 8 and MAX]++
            histograms[2][c and MAX]++
            count++
        }
        if (count == 0) return null
        return IntArray(CHANNELS) { k ->
            var seen = 0
            histograms[k].indexOfFirst { n ->
                seen += n
                seen * 2 >= count
            }
        }
    }
}
