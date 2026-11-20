package com.a207.runkk.app.core.avatar

import android.graphics.Path

/**
 * 몸 쪽 목 층의 자리(머리 1024 캔버스). 머리 레이어는 riv에서 몸 텍스처보다 아래에 그려지므로(서기 아틀라스·달리기 몸통이 얼굴 위),
 * 머리 목이 짧거나 가늘면 옷 깃 안쪽에 피부도 옷도 없는 틈이 보였다. 머리 피부색으로 이 다각형을 머리 아래에 깔면 깃 안은 몸 목 폭으로
 * 늘 채워진다. 값은 몸체(남녀·정면/측면)의 것이며 특정 인물의 좌표가 아니다.
 *
 * 다각형은 몸 텍스처 좌표로 적고 riv 셋업 자세의 몸 메시 → 머리 캔버스 변환([atlasMatrix], x = u·a + v·b + c, y = u·d + v·e + f)으로
 * 옮긴다. 정면은 서기 아틀라스(512×768, 남녀 공용 맨몸)의 목 구멍(어깨선 U자 안쪽, OUTFIT_SPEC의 목 기둥 x 217~295가 가운데)이고 목
 * 둘레 꼭짓점에서 오차가 없다. 측면은 달리기 몸통(256×384)의 목 조각 뒤 가장자리부터 턱 중심 앞 [SIDE_FRONT]까지이고, 몸통 메시가 휘어
 * 있어 선형 근사다. 위쪽은 턱 위 [ABOVE_CHIN]까지 올려 턱 아래가 비지 않게 한다. 정면 목 구멍 가운데 x는 [HeadTarget]의 centerX와 같다.
 *
 * 측면 머리는 턱 아래([SIDE_TRIM_BELOW_CHIN])에서 이 다각형 밖의 피부를 지운다: 목 폭을 몸 목 폭에 맞추고, 목 뒤는 목 조각 외곽선에 잇는다.
 */
internal enum class BodyNeck(val target: HeadTarget, private val atlasMatrix: FloatArray) {
    FEMALE_FRONT(HeadTarget.FEMALE_FRONT, floatArrayOf(1.1171f, 0f, 248.03f, 0f, .8841f, 779.92f)),
    MALE_FRONT(HeadTarget.MALE_FRONT, floatArrayOf(1.1553f, 0f, 224.25f, 0f, .9697f, 853.05f)),
    FEMALE_SIDE(HeadTarget.FEMALE_SIDE, floatArrayOf(.6715f, 0f, 538.78f, -.0038f, .766f, 786.87f)),
    MALE_SIDE(HeadTarget.MALE_SIDE, floatArrayOf(.7067f, -.0149f, 484.17f, -.0042f, .8479f, 848.05f));

    val isFront: Boolean get() = this == FEMALE_FRONT || this == MALE_FRONT

    /** 몸 텍스처 → 머리 캔버스 변환(android.graphics.Matrix.setValues 순서의 앞 여섯 값). */
    val atlasToHead: FloatArray get() = atlasMatrix.copyOf()

    /** 다각형 꼭짓점(x, y 번갈아, 머리 캔버스). */
    val outline: FloatArray by lazy { if (isFront) frontOutline() else sideOutline() }

    /** 목 층을 그릴 경로. */
    fun path(): Path = Path().apply {
        moveTo(outline[0], outline[1])
        for (i in 2 until outline.size step 2) lineTo(outline[i], outline[i + 1])
        close()
    }

    /** 다각형이 [y] 행에서 차지하는 가로 범위(왼쪽, 오른쪽). 그 행에 없으면 null. */
    fun spanAt(y: Float): Pair<Float, Float>? {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        val count = outline.size / 2
        for (i in 0 until count) {
            val x1 = outline[i * 2]
            val y1 = outline[i * 2 + 1]
            val x2 = outline[(i + 1) % count * 2]
            val y2 = outline[(i + 1) % count * 2 + 1]
            if (y1 != y2 && y >= minOf(y1, y2) && y <= maxOf(y1, y2)) {
                val x = x1 + (y - y1) / (y2 - y1) * (x2 - x1)
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        return if (left <= right) left to right else null
    }

    /** 측면 머리 피부를 다각형 밖에서 지우기 시작하는 행(턱 아래). 정면은 지우지 않는다(목 구멍 밖은 몸이 덮는다). */
    val trimFromY: Float? get() = if (isFront) null else target.chinY + SIDE_TRIM_BELOW_CHIN

    private fun x(u: Float, v: Float) = u * atlasMatrix[0] + v * atlasMatrix[1] + atlasMatrix[2]

    private fun y(u: Float, v: Float) = u * atlasMatrix[3] + v * atlasMatrix[4] + atlasMatrix[5]

    private fun FloatArray.at(u: Float, v: Float) = floatArrayOf(x(u, v), y(u, v))

    private fun frontOutline(): FloatArray {
        val top = target.chinY - ABOVE_CHIN
        // 어깨선이 시작하는 행(v 4) 위는 목 기둥 폭만: 넓히면 어깨 위 빈 곳에 피부가 삐져나온다
        val points = mutableListOf(
            floatArrayOf(x(NECK_RIGHT, 0f), top),
            atlasMatrix.at(NECK_RIGHT, SHOULDER_V)
        )
        STAND_OPENING_RIGHT.forEach { (u, v) -> points += atlasMatrix.at(u, v) }
        STAND_OPENING_LEFT.forEach { (u, v) -> points += atlasMatrix.at(u, v) }
        points += atlasMatrix.at(NECK_LEFT, SHOULDER_V)
        points += floatArrayOf(x(NECK_LEFT, 0f), top)
        return points.flatMap { it.asList() }.toFloatArray()
    }

    private fun sideOutline(): FloatArray {
        val top = target.chinY - ABOVE_CHIN
        val front = target.centerX + SIDE_FRONT
        val (topU, topV) = RUN_STUB_BACK.first()
        val (bottomU, bottomV) = RUN_STUB_BACK.last()
        val points = mutableListOf(
            floatArrayOf(x(topU, topV), top),
            floatArrayOf(front, top),
            floatArrayOf(front, y(bottomU, bottomV))
        )
        RUN_STUB_BACK.reversed().forEach { (u, v) -> points += atlasMatrix.at(u, v) }
        return points.flatMap { it.asList() }.toFloatArray()
    }

    companion object {
        /** 목 층 윗변: 턱 위 이만큼(얼굴이 덮는다). */
        const val ABOVE_CHIN = 16f

        /** 측면 머리 피부를 다각형 밖에서 지우기 시작하는 턱 아래 거리. */
        const val SIDE_TRIM_BELOW_CHIN = 4f

        /** 측면: 턱 중심에서 목 앞(목젖 쪽)까지. 그 아래는 몸통 앞 깃이 덮는다. */
        private const val SIDE_FRONT = 24f

        /** 목 기둥(서기 아틀라스 x 217~295, 끝 포함). */
        private const val NECK_LEFT = 217f
        private const val NECK_RIGHT = 296f

        /** 서기 아틀라스에서 어깨선 획이 시작하는 행. */
        private const val SHOULDER_V = 4f

        /**
         * 맨몸 서기 아틀라스 목 구멍의 오른쪽 가장자리(위 → 아래, 아틀라스 좌표). 어깨선 획 안쪽 끝(y 4~10: x 195·319)과 목 구멍
         * 가장자리보다 2~3px 바깥이라 경계가 몸 외곽선 아래에 숨는다. y 44는 구멍이 닫힌 아래다.
         */
        private val STAND_OPENING_RIGHT = listOf(
            321f to 4f, 321f to 10f, 305f to 13f, 302f to 16f, 296f to 20f, 290f to 25f, 280f to 30f, 266f to 40f,
            262f to 44f
        )

        /** 같은 구멍의 왼쪽 가장자리(아래 → 위). */
        private val STAND_OPENING_LEFT = listOf(
            257f to 44f, 254f to 40f, 244f to 30f, 233f to 25f, 227f to 20f, 221f to 16f, 218f to 13f, 195f to 10f,
            195f to 4f
        )

        /**
         * 달리기 몸통 목 조각의 뒤 가장자리(위 → 아래, 텍스처 좌표, 실루엣보다 2px 안쪽이라 경계가 외곽선 아래에 숨는다). v 40 아래는
         * 몸통 앞 깃이 목 앞을 덮는다.
         */
        private val RUN_STUB_BACK = listOf(
            105f to 0f, 97f to 3f, 92f to 6f, 86f to 10f, 78f to 15f, 72f to 20f, 66f to 25f, 62f to 30f, 53f to 40f
        )

        fun front(gender: AvatarGender): BodyNeck = when (gender) {
            AvatarGender.FEMALE -> FEMALE_FRONT
            AvatarGender.MALE -> MALE_FRONT
        }

        fun side(gender: AvatarGender): BodyNeck = when (gender) {
            AvatarGender.FEMALE -> FEMALE_SIDE
            AvatarGender.MALE -> MALE_SIDE
        }
    }
}
