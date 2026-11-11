package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 프로필 원에 셀카 머리 층(GET /avatar/heads 정면 세 층, 1024 공통 캔버스)을 그릴 구도([임시:D-성-189]). 셀카 머리는 캔버스 가운데에
 * 작게 있고 목이 짧아 그대로 맞추면 목이 원 안에서 떠 보였다. 기본 머리 그림([RunkkAvatarArt.defaultHeadOf])과 같은 크기·자리가
 * 되도록 정수리(헤어 층 알파 위 끝)·턱·가운데를 [Reference]에 맞추고, 그래도 목 끝이 상자 바닥에 닿지 않으면 목 끝 줄([neckRow])을
 * 바닥까지 이어 그려 원 아래 가장자리에서 잘리게 한다. 값은 상자 한 변에 대한 비다.
 *
 * 목·턱은 얼굴 층 알파에서 찾는다(`:core:avatar` GeneratedHeadGeometry와 같은 규칙. `:core:ui`가 `:core:avatar`에 기대지 않게 옮겼다).
 * 찾지 못하면 구도를 정하지 않고 부르는 쪽이 지금처럼 캔버스를 상자에 맞춘다.
 *
 * @property scale 원본 1px이 상자 한 변의 몇 배인가
 * @property dx 원본 x 0이 놓일 자리(상자 한 변 대비)
 * @property dy 원본 y 0이 놓일 자리(상자 한 변 대비)
 * @property neckRow 상자 바닥까지 이어 그릴 얼굴 층의 목 줄(원본 y). 목이 이미 바닥에 닿으면 null
 */
internal data class PortraitHeadFrame(val scale: Float, val dx: Float, val dy: Float, val neckRow: Int?) {
    fun mapX(x: Float): Float = x * scale + dx

    fun mapY(y: Float): Float = y * scale + dy

    /** 기본 머리 그림 안의 정수리·턱·가운데 x(상자 한 변 대비). */
    data class Reference(val crownY: Float, val chinY: Float, val centerX: Float)

    /** 목 가운데 x, 턱 y, 불투명 마지막 줄 다음 y(원본 픽셀). */
    private data class Neck(val centerX: Float, val chinY: Float, val bottom: Int)

    private data class Span(val left: Int, val right: Int) {
        val width get() = right - left + 1
        val center get() = (left + right) / 2f
    }

    companion object {
        /**
         * 기본 머리 그림은 Rive 몸체 1024 캔버스의 기본 머리 정면 합성을 정사각으로 자른 것이다(남: 왼쪽 위 29.1·79.5, 한 변 983.1,
         * 여: 55.7·39.0, 한 변 948.0). 생성 머리 목표 자리(`:core:avatar` HeadTarget, 남 정수리 59·턱 854·가운데 520, 여 64·787·534)를
         * 그 자르기로 옮긴 값이다. 기본 머리 그림을 다시 만들면 함께 고친다.
         */
        private val MALE = Reference(crownY = -0.0209f, chinY = 0.7878f, centerX = 0.4993f)
        private val FEMALE = Reference(crownY = 0.0264f, chinY = 0.7890f, centerX = 0.5045f)

        /** 목·턱을 볼 불투명 알파. */
        private const val OPAQUE = 200

        /** 정수리를 볼 알파(생성 머리 배치와 같은 기준). */
        private const val CROWN_ALPHA = 128

        /** 정수리~턱이 캔버스의 이 비보다 짧으면 머리로 보지 않는다. */
        private const val MIN_SPAN = .25f

        fun referenceOf(character: AvatarCharacter): Reference = when (character) {
            AvatarCharacter.MALE -> MALE
            AvatarCharacter.FEMALE -> FEMALE
        }

        /**
         * 얼굴 층 [face]·헤어 층 [hair](ARGB 픽셀, [width]×[height])의 구도. 목을 찾지 못했거나 헤어가 비었거나 크기가 맞지 않으면 null.
         */
        fun of(
            face: IntArray,
            hair: IntArray,
            width: Int,
            height: Int,
            character: AvatarCharacter
        ): PortraitHeadFrame? {
            if (width <= 0 || height <= 0 || face.size != width * height || hair.size != width * height) return null
            val neck = neckOf(face, width, height) ?: return null
            val crown = crownOf(hair, width, height) ?: return null
            val span = neck.chinY - crown
            if (span < height * MIN_SPAN) return null
            val reference = referenceOf(character)
            val scale = (reference.chinY - reference.crownY) / span
            val dx = reference.centerX - neck.centerX * scale
            val dy = reference.chinY - neck.chinY * scale
            val reachesBottom = neck.bottom * scale + dy >= 1f
            // 목 끝은 그림자·외곽선이 있을 수 있어 끝에서 목 길이의 4분의 1 위 줄을 잇는다(그 아래는 얼굴 층이 덮는다).
            val row = if (reachesBottom) {
                null
            } else {
                (neck.bottom - 1 - maxOf(2, ((neck.bottom - neck.chinY) / 4).roundToInt()))
                    .coerceAtLeast(neck.chinY.roundToInt())
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
