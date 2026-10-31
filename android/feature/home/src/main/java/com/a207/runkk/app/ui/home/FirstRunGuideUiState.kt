package com.a207.runkk.app.ui.home

/**
 * 첫 사용 가이드 진행(D-277). 단계 목록은 화면이 ReleaseScope로 거른 것(GuideStep.visibleIn)이라 ViewModel은 몇 번째인지만 안다.
 */
data class FirstRunGuideUiState(val isVisible: Boolean = false, val index: Int = 0) {
    /** [stepCount]단계 가운데 마지막인가(마지막은 "시작하기"만 보인다). */
    fun isLastOf(stepCount: Int): Boolean = index >= stepCount - 1
}
