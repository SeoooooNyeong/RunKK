package com.a207.runkk.app.core.avatar

/**
 * 머리 한 장의 닮음 변환(균일 배율 + 이동). 얼굴, 눈, 헤어(정면·측면 각각)에 같은 값을 써서 세 층이 어긋나지 않는다.
 * 배율은 (목표 턱 − 목표 정수리) / (턱 − 정수리)를 [MIN_SCALE]..[MAX_SCALE]로 묶고, 턱 중심이 목표 자리에 오게 옮긴다.
 */
internal data class HeadPlacement(val scale: Float, val dx: Float, val dy: Float) {
    fun mapX(x: Float): Float = x * scale + dx

    fun mapY(y: Float): Float = y * scale + dy

    companion object {
        const val MIN_SCALE = .5f
        const val MAX_SCALE = 1.8f

        fun fit(centerX: Float, chinY: Float, crownY: Float, target: HeadTarget): HeadPlacement {
            val scale = ((target.chinY - target.crownY) / (chinY - crownY)).coerceIn(MIN_SCALE, MAX_SCALE)
            return HeadPlacement(scale, target.centerX - centerX * scale, target.chinY - chinY * scale)
        }
    }
}
