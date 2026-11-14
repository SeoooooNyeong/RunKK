package com.a207.runkk.app.ui.home

/**
 * 첫 사용 가이드 진행([임시:D-성-296], D-277). 시작 카드([GuidePhase.START_CARD])에서 「둘러보기」를 고르면 단계([GuidePhase.STEPS])의
 * [index]번째 말풍선을 보인다. 단계 목록은 화면이 ReleaseScope로 거른 것(GuideStep.visibleIn)이라 ViewModel은 몇 번째인지만 안다.
 * [isReplay]는 설정의 「가이드 다시 보기」로 연 것이다: 시작 카드 없이 1단계부터 보이고, 끝내도 본 것으로 저장하지 않는다.
 */
data class FirstRunGuideUiState(
    val phase: GuidePhase = GuidePhase.HIDDEN,
    val index: Int = 0,
    val isReplay: Boolean = false
) {
    /** 가이드가 보이는 중인가(시작 카드 또는 단계). */
    val isVisible: Boolean get() = phase != GuidePhase.HIDDEN

    /** [stepCount]단계 가운데 마지막인가(마지막 단추는 "시작하기"다). */
    fun isLastOf(stepCount: Int): Boolean = index >= stepCount - 1
}
