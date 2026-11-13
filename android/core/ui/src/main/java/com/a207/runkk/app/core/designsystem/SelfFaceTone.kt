package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * 내 셀카 얼굴의 그을림([LocalSelfFaceTone]). 몸(Rive)과 같은 규칙으로 얼굴 원의 피부 층도 그을린다: 러닝량 0~100,
 * 100이어도 가장 어두운 톤의 60%까지, 곱만 쓴다(core.avatar SkinToneFilter와 같은 값, [임시:D-성-220]).
 * 눈·헤어 층에는 쓰지 않는다. [faceKey]가 같은 얼굴에만 쓴다(다른 러너 얼굴은 그대로). 랭킹 줄처럼 다른 러너 얼굴을 그을릴 때는
 * 그 얼굴 원을 그 러너의 얼굴 키·러닝량 값으로 감싸 내려 준다(S15P31A207-300).
 *
 * @property faceKey 그을릴 얼굴 키("head:…", 보통 내 얼굴), 모르면 null
 * @property runningAmount 그 얼굴 주인의 러닝량 0~100
 */
data class SelfFaceTone(val faceKey: String? = null, val runningAmount: Int = 0) {
    /** [key] 얼굴의 피부 층에 씌울 색 거르개. 내 얼굴이 아니거나 러닝량이 0이면 null(원본 그대로). */
    fun skinFilterFor(key: String?): ColorFilter? {
        if (key == null || key != faceKey || runningAmount <= 0) return null
        val fraction = MAX_TAN * runningAmount.coerceAtMost(MAX_AMOUNT) / MAX_AMOUNT.toFloat()
        return ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1f - RED_DROP * fraction, 0f, 0f, 0f, 0f,
                    0f, 1f - GREEN_DROP * fraction, 0f, 0f, 0f,
                    0f, 0f, 1f - BLUE_DROP * fraction, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    private companion object {
        const val MAX_AMOUNT = 100
        const val MAX_TAN = .60f
        const val RED_DROP = .30f
        const val GREEN_DROP = .50f
        const val BLUE_DROP = .68f
    }
}
