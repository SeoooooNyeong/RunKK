package com.a207.runkk.app.core.avatar

import com.a207.runkk.app.core.model.avatar.SkinTone

/**
 * 러닝량 → 피부 색 행렬(ColorMatrix 4×5). 곱만 써서 원본 그라데이션과 알파를 지키고, 러닝량 0이면 원본을 그대로 쓴다.
 * 피부 레이어에만 쓴다. 눈, 헤어, 안경, 옷에는 쓰지 않는다.
 * 러닝량 100이어도 가장 어두운 톤의 [SkinTone.MAX_TAN](40%)까지만 어두워진다. 값은 얼굴 원(core.ui SelfFaceTone)과 같은
 * [SkinTone]에서 온다.
 */
internal object SkinToneFilter {
    fun matrix(amount: Int): FloatArray {
        require(amount in MIN_RUNNING_AMOUNT..MAX_RUNNING_AMOUNT) { "running amount must be 0..100" }
        val (red, green, blue) = SkinTone.channelScales(amount)
        return floatArrayOf(
            red, 0f, 0f, 0f, 0f,
            0f, green, 0f, 0f, 0f,
            0f, 0f, blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
