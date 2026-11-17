package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 프로필 원에 셀카 머리 층(GET /avatar/heads 정면 세 층, 1024 공통 캔버스)을 그릴 구도(D-821). 머리 크기와 가운데는
 * [TARGET](정수리~턱이 상자의 91%, 목 가운데 50%)을 따른다.
 *
 * - 원 안([of]의 isInCircle 참): 서 있는 아바타처럼 생성 목 그림을 늘리지 않고 그대로 쓴다. 머리는 정수리~턱이 상자의
 *   [IN_CIRCLE_SPAN]이고, 목 맨 아래 줄을 [NECK_BOTTOM_Y](원 가장자리 바로 밖)에 둬 턱 아래 목이 짧게 보이고 둥근 목 끝 외곽선은 원
 *   밖에서 잘린다. 정수리가 [CROWN_MIN]..[CROWN_MAX] 밖으로 나가면 그 끝에 맞추고, 그래도 목 끝이 바닥에 닿지 않으면(아주 짧은 목) 목이
 *   끝나기 직전 줄([neckRow])을 바닥까지 늘린다(D-853, 2026-11-13 PM "기본 캐릭터는 목이 있는데 직접 만든 건 목이 없다",
 *   "아바타 서있을 때 쓴 목으로").
 * - 원 밖(대결 그림·지도 마커 등, D-825): [TARGET] 그대로(정수리 -5%, 턱 86%) 두고 목은 제 길이로 둥글게 끝난다.
 *
 * 기본 머리 그림([RunkkAvatarArt.defaultHeadOf])도 같은 규칙으로 만든다(core/ui/tools/default-head). 값은 상자 한 변에 대한 비다.
 *
 * 목·턱은 얼굴 층 알파에서 찾는다(`:core:avatar` GeneratedHeadGeometry와 같은 규칙. `:core:ui`가 `:core:avatar`에 기대지 않게 옮겼다).
 * 찾지 못하면 구도를 정하지 않고 부르는 쪽이 캔버스를 상자에 맞춘다.
 *
 * @property scale 원본 1px이 상자 한 변의 몇 배인가
 * @property dx 원본 x 0이 놓일 자리(상자 한 변 대비)
 * @property dy 원본 y 0이 놓일 자리(상자 한 변 대비)
 * @property neckRow 상자 바닥까지 늘려 그릴 얼굴 층의 목 줄(원본 y). 이 줄 아래 얼굴 층은 그리지 않는다. 늘리지 않으면 null
 */
internal data class PortraitHeadFrame(val scale: Float, val dx: Float, val dy: Float, val neckRow: Int?) {
    fun mapX(x: Float): Float = x * scale + dx

    fun mapY(y: Float): Float = y * scale + dy

    /** 원본을 [factor]배 줄여 읽은 그림(가로 원본/[factor])에 쓸 구도. 상자에 놓이는 자리는 같다. */
    fun scaledBy(factor: Float): PortraitHeadFrame =
        if (factor == 1f) this else copy(scale = scale * factor, neckRow = neckRow?.let { (it / factor).toInt() })

    /** 정수리·턱·가운데 x(상자 한 변 대비). */
    data class Reference(val crownY: Float, val chinY: Float, val centerX: Float)

    /**
     * 목 가운데 x, 턱 y, 목이 둥글게 끝나기 시작하는 줄(목이 가장 넓은 줄 뒤 폭이 줄어드는 첫 줄) [end], 불투명 마지막 줄 다음 y(원본 픽셀).
     */
    private data class Neck(val centerX: Float, val chinY: Float, val end: Int, val bottom: Int)

    private data class Span(val left: Int, val right: Int) {
        val width get() = right - left + 1
        val center get() = (left + right) / 2f
    }

    companion object {
        /**
         * 머리 크기·가운데(남녀 같음): 정수리~턱이 상자 높이의 91%, 목 가운데는 상자 가운데. 이때 머리(헤어 포함) 너비가 원 지름의 약
         * 90%다. 원 밖 그림은 이 자리(정수리 -5%, 턱 86%) 그대로다. 기본 머리 그림 생성 도구(core/ui/tools/default-head)와 같은 값이다.
         */
        val TARGET = Reference(crownY = -0.05f, chinY = 0.86f, centerX = 0.5f)

        /** 원 안 머리 크기: 정수리~턱이 상자 높이의 85%(서 있는 아바타처럼 턱 아래 목이 짧게 보이는 크기). */
        const val IN_CIRCLE_SPAN = 0.85f

        /** 원 안에서 생성 목의 맨 아래 줄이 놓일 자리: 원 아래 가장자리 바로 밖(둥근 목 끝 외곽선이 보이지 않는다). */
        const val NECK_BOTTOM_Y = 1.02f

        /** 원 안에서 머리를 내릴 때 정수리가 놓일 수 있는 범위(상자 한 변 대비). 기본 머리 그림은 약 2~3%다. */
        const val CROWN_MIN = -0.05f
        const val CROWN_MAX = 0.08f

        /** 목 끝(둥글어지기 시작하는 줄)을 볼 때 목 최대 폭에 대한 비. */
        private const val NECK_END_RATIO = .97f

        /** 목을 늘릴 때 목 끝 줄에서 이만큼 위 줄을 늘린다(목 끝 외곽선을 피한다). */
        private const val STRETCH_MARGIN = 2

        /** 목·턱을 볼 불투명 알파. */
        private const val OPAQUE = 200

        /** 정수리를 볼 알파(생성 머리 배치와 같은 기준). */
        private const val CROWN_ALPHA = 128

        /** 정수리~턱이 캔버스의 이 비보다 짧으면 머리로 보지 않는다. */
        private const val MIN_SPAN = .25f

        /**
         * 얼굴 층 [face]·헤어 층 [hair](ARGB 픽셀, [width]×[height])의 구도. [isInCircle]이면 원 안 구도(목 제 길이, 머리를 내림), 아니면
         * 원 밖 구도다. 목을 찾지 못했거나 헤어가 비었거나 크기가 맞지 않으면 null.
         */
        fun of(
            face: IntArray,
            hair: IntArray,
            width: Int,
            height: Int,
            isInCircle: Boolean = true
        ): PortraitHeadFrame? {
            if (width <= 0 || height <= 0 || face.size != width * height || hair.size != width * height) return null
            val neck = neckOf(face, width, height) ?: return null
            val crown = crownOf(hair, width, height) ?: return null
            val span = neck.chinY - crown
            if (span < height * MIN_SPAN) return null
            if (!isInCircle) {
                val scale = (TARGET.chinY - TARGET.crownY) / span
                return PortraitHeadFrame(
                    scale,
                    TARGET.centerX - neck.centerX * scale,
                    TARGET.chinY - neck.chinY * scale,
                    null
                )
            }
            // 원 안: 서 있는 아바타처럼 생성 목 그림을 늘리지 않고 그대로 둔다. 목 맨 아래 줄을 원 가장자리 바로 밖에 두고, 정수리가
            // 범위를 벗어나면 그 끝에 맞춘다(D-853).
            val scale = IN_CIRCLE_SPAN / span
            val dx = TARGET.centerX - neck.centerX * scale
            val dy = (NECK_BOTTOM_Y - neck.bottom * scale).coerceIn(
                CROWN_MIN - crown * scale,
                CROWN_MAX - crown * scale
            )
            val reachesBottom = neck.end * scale + dy >= 1f - 1e-4f
            // 아주 짧은 목: 목 끝 외곽선 바로 위 줄을 바닥까지 늘린다.
            val row = if (reachesBottom) {
                null
            } else {
                (neck.end - 1 - STRETCH_MARGIN).coerceAtLeast(neck.chinY.roundToInt())
            }
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
                    val chin = firstWider + 1
                    return Neck(reference.center, chin.toFloat(), neckEnd(rows, chin, bottom), bottom)
                }
            }
            return null
        }

        /**
         * 턱 [chin]부터 목 끝 [bottom] 사이에서 목 폭이 목 폭 중앙값의 [NECK_END_RATIO] 이상인 마지막 줄 다음(둥근 끝이 시작하는 줄).
         * 최대 폭이 아니라 중앙값을 본다: 생성 목은 턱 바로 아래가 가장 넓어(턱과 이어지는 곳) 최대 폭으로 보면 목 끝이 턱 바로 다음
         * 줄로 잡혀 목이 없는 것으로 보였다(D-853).
         */
        private fun neckEnd(rows: Array<Span?>, chin: Int, bottom: Int): Int {
            val widths = (chin until bottom).map { rows[it]?.width ?: 0 }.filter { it > 0 }.sorted()
            val median = widths.getOrNull(widths.size / 2) ?: 0
            val last =
                (chin until bottom).lastOrNull { (rows[it]?.width ?: 0) >= median * NECK_END_RATIO } ?: (bottom - 1)
            return last + 1
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
