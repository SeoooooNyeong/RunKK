package com.a207.runkk.app.domain.sync

/**
 * 시작 에셋 미리 받기 진행(S15P31A207-345): 받은 파일 수 [done] / 받을 파일 수 [total]. 받을 것이 없으면(total 0) 바로 완료이고
 * 막대를 그리지 않는다.
 */
data class PreloadProgress(val done: Int, val total: Int) {
    /** 0~1. 받을 것이 없으면 1. */
    val fraction: Float get() = if (total <= 0) 1f else done.coerceIn(0, total).toFloat() / total

    /** 0~100(버림). 다 받았을 때만 100이다. */
    val percent: Int get() = if (total <= 0) FULL else done.coerceIn(0, total) * FULL / total

    val isComplete: Boolean get() = done >= total

    /** 받을 것이 있어 막대를 그린다. */
    val hasBar: Boolean get() = total > 0

    companion object {
        /** 아직 목록을 모름(막대 없음). */
        val UNKNOWN = PreloadProgress(0, 0)
        private const val FULL = 100
    }
}
