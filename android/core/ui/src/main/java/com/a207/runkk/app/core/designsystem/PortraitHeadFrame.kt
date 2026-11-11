package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 프로필 원에 셀카 머리 층(GET /avatar/heads 정면 세 층, 1024 공통 캔버스)을 그릴 구도([임시:D-성-189]). 상용 게임 프로필처럼 머리를 크게
 * 놓고 짧은 목이 원 아래 가장자리에서 잘리게 한다: 정수리(헤어 층 알파 위 끝)·턱·목 가운데를 [TARGET](정수리 -5%, 턱 86%, 가운데)에
 * 맞추고, 목 끝이 상자 바닥에 닿지 않으면 턱과 목 끝 사이 가운데 줄([neckRow])을 상자 바닥까지 목 폭 그대로 늘린다. 그 줄 아래 얼굴 층(목
 * 끝 외곽선)은 그리지 않는다. 기본 머리 그림([RunkkAvatarArt.defaultHeadOf])도 같은 구도로 만든다(core/ui/tools/default-head). 값은
 * 상자 한 변에 대한 비다.
 *
 * 목·턱은 얼굴 층 알파에서 찾는다(`:core:avatar` GeneratedHeadGeometry와 같은 규칙. `:core:ui`가 `:core:avatar`에 기대지 않게 옮겼다).
 * 찾지 못하면 구도를 정하지 않고 부르는 쪽이 캔버스를 상자에 맞춘다.
 *
 * @property scale 원본 1px이 상자 한 변의 몇 배인가
 * @property dx 원본 x 0이 놓일 자리(상자 한 변 대비)
 * @property dy 원본 y 0이 놓일 자리(상자 한 변 대비)
 * @property neckRow 상자 바닥까지 늘려 그릴 얼굴 층의 목 줄(원본 y). 이 줄 아래 얼굴 층은 그리지 않는다. 목이 이미 바닥에 닿으면 null
 */
internal data class PortraitHeadFrame(val scale: Float, val dx: Float, val dy: Float, val neckRow: Int?) {
    fun mapX(x: Float): Float = x * scale + dx

    fun mapY(y: Float): Float = y * scale + dy

    /** 정수리·턱·가운데 x(상자 한 변 대비). */
    data class Reference(val crownY: Float, val chinY: Float, val centerX: Float)

    /** 목 가운데 x, 턱 y, 불투명 마지막 줄 다음 y(원본 픽셀). */
    private data class Neck(val centerX: Float, val chinY: Float, val bottom: Int)

    private data class Span(val left: Int, val right: Int) {
        val width get() = right - left + 1
        val center get() = (left + right) / 2f
    }

    companion object {
        /**
         * 프로필 원 목표 구도(남녀 같음): 정수리는 원 위 끝에서 살짝 잘리고(-5%), 턱은 원 높이 86%, 목 가운데는 원 가운데. 이때 머리(헤어
         * 포함) 너비가 원 지름의 약 90%다. 기본 머리 그림 생성 도구(core/ui/tools/default-head)와 같은 값이다.
         */
        val TARGET = Reference(crownY = -0.05f, chinY = 0.86f, centerX = 0.5f)

        /** 목·턱을 볼 불투명 알파. */
        private const val OPAQUE = 200

        /** 정수리를 볼 알파(생성 머리 배치와 같은 기준). */
        private const val CROWN_ALPHA = 128

        /** 정수리~턱이 캔버스의 이 비보다 짧으면 머리로 보지 않는다. */
        private const val MIN_SPAN = .25f

        /**
         * 얼굴 층 [face]·헤어 층 [hair](ARGB 픽셀, [width]×[height])의 구도. 목을 찾지 못했거나 헤어가 비었거나 크기가 맞지 않으면 null.
         */
        fun of(face: IntArray, hair: IntArray, width: Int, height: Int): PortraitHeadFrame? {
            if (width <= 0 || height <= 0 || face.size != width * height || hair.size != width * height) return null
            val neck = neckOf(face, width, height) ?: return null
            val crown = crownOf(hair, width, height) ?: return null
            val span = neck.chinY - crown
            if (span < height * MIN_SPAN) return null
            val scale = (TARGET.chinY - TARGET.crownY) / span
            val dx = TARGET.centerX - neck.centerX * scale
            val dy = TARGET.chinY - neck.chinY * scale
            val reachesBottom = neck.bottom * scale + dy >= 1f
            // 목 끝에는 외곽선이 있어 그 줄을 늘리면 목에 가로 줄이 남는다. 턱 그림자와 목 끝 외곽선 사이 가운데 줄을 늘린다.
            val row = if (reachesBottom) null else ((neck.chinY + neck.bottom) / 2).roundToInt()
            return PortraitHeadFrame(scale, dx, dy, row)
        }

        /** 헤어 층 알파가 [CROWN_ALPHA] 이상인 첫 줄. 없으면 null. */
        private fun crownOf(hair: IntArray, width: Int, height: Int): Float? {
            for (y in 0 until height) {
                for (x in 0 until width) if (hair[y * width + x].ushr(24) >= CROWN_ALPHA) return y.toFloat()
            }
            return null
        }

        /** 얼굴 층에서 목(아래쪽 좁은 띠)과 턱(목이 넓어지기 시작하는 줄)을 찾는다. 없으면 null. */
        private fun neckOf(pixels: IntArray, width: Int, height: Int): Neck? {
            val minRun = maxOf(8, width / 100)
            val rows = Array(height) { y -> widestRun(pixels, width, y, minRun) }
            val bottom = rows.indexOfLast { it != null } + 1
            if (bottom <= 0) return null
            val maxWidth = rows.filterNotNull().maxOf { it.width }
            val referenceY = bottom - maxOf(8, (maxWidth * .025f).roundToInt())
            val reference = rows.getOrNull(referenceY)
            if (reference == null || reference.width >= maxWidth * .55f) return null
            val limit = maxOf(0, bottom - maxOf(60, (maxWidth * .45f).roundToInt()))
            var widerRows = 0
            var firstWider = -1
            for (y in referenceY downTo limit) {
                val row = rows[y]
                val wider = row != null && row.width > reference.width * 1.45f &&
                    row.width - reference.width > minRun && abs(row.center - reference.center) < maxWidth * .25f
                if (!wider) {
                    widerRows = 0
                    firstWider = -1
                    continue
                }
                if (widerRows == 0) firstWider = y
                widerRows++
                if (widerRows >= 3) {
                    if (referenceY - firstWider < maxOf(3, height / 200)) return null
                    return Neck(reference.center, (firstWider + 1).toFloat(), bottom)
                }
            }
            return null
        }

        private fun widestRun(pixels: IntArray, width: Int, y: Int, minRun: Int): Span? {
            var best: Span? = null
            var start = -1
            for (x in 0..width) {
                val opaque = x < width && pixels[y * width + x].ushr(24) >= OPAQUE
                if (opaque && start < 0) start = x
                if (!opaque && start >= 0) {
                    if (x - start >= minRun && x - start > (best?.width ?: 0)) best = Span(start, x - 1)
                    start = -1
                }
            }
            return best
        }
    }
}
