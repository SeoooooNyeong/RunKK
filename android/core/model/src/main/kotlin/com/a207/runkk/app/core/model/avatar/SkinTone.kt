package com.a207.runkk.app.core.model.avatar

/**
 * 러닝량에 따른 피부 그을림 규칙 값. 몸(core.avatar SkinToneFilter, Rive 피부 텍스처)과 얼굴 원(core.ui SelfFaceTone)이 같은 값을
 * 쓰도록 한 곳에 둔다(두 모듈이 공통으로 의존하는 모듈은 core.model뿐이다).
 *
 * - 러닝량 0~100에 비례하고, 100이어도 가장 어두운 톤의 [MAX_TAN](40%, PM 2026-11-18)까지만 어두워진다.
 * - 채널마다 곱만 쓴다(원본 그라데이션과 알파를 지킨다). 가장 어두운 톤은 R·G·B를 [RED_DROP]·[GREEN_DROP]·[BLUE_DROP]만큼 줄인 색이다.
 */
object SkinTone {
    const val MAX_TAN = .40f
    const val MAX_RUNNING_AMOUNT = 100
    const val RED_DROP = .30f
    const val GREEN_DROP = .50f
    const val BLUE_DROP = .68f

    /** 러닝량 [amount](범위 밖은 0~100으로 자른다)의 그을림 정도 0~[MAX_TAN]. */
    fun tanFraction(amount: Int): Float =
        MAX_TAN * amount.coerceIn(0, MAX_RUNNING_AMOUNT) / MAX_RUNNING_AMOUNT.toFloat()

    /** 러닝량 [amount]의 R·G·B 곱 배율(0이면 모두 1). */
    fun channelScales(amount: Int): FloatArray {
        val fraction = tanFraction(amount)
        return floatArrayOf(1f - RED_DROP * fraction, 1f - GREEN_DROP * fraction, 1f - BLUE_DROP * fraction)
    }
}
