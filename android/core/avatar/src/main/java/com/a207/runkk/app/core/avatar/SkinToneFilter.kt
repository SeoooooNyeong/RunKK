package com.a207.runkk.app.core.avatar

/**
 * 러닝량 → 피부 색 행렬(ColorMatrix 4×5). 곱만 써서 원본 그라데이션과 알파를 지키고, 러닝량 0이면 원본을 그대로 쓴다.
 * 피부 레이어에만 쓴다. 눈, 헤어, 안경, 옷에는 쓰지 않는다.
 */
internal object SkinToneFilter {
    private const val RED_DROP = .30f
    private const val GREEN_DROP = .50f
    private const val BLUE_DROP = .68f

    fun matrix(amount: Int): FloatArray {
        require(amount in MIN_RUNNING_AMOUNT..MAX_RUNNING_AMOUNT) { "running amount must be 0..100" }
        val fraction = amount / MAX_RUNNING_AMOUNT.toFloat()
        return floatArrayOf(
            1f - RED_DROP * fraction, 0f, 0f, 0f, 0f,
            0f, 1f - GREEN_DROP * fraction, 0f, 0f, 0f,
            0f, 0f, 1f - BLUE_DROP * fraction, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
