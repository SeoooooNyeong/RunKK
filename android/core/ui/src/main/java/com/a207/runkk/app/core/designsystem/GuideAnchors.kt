package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

/**
 * 첫 사용 가이드 앵커 표(D-277): 단계 → 화면(루트) 좌표의 경계. 홈 카드·주간 막대와 하단 탭이 `Modifier.guideAnchor`로 적고,
 * 가이드 오버레이가 읽어 강조 자리와 말풍선 위치를 정한다. 값이 바뀌면 읽는 쪽이 다시 그려진다.
 */
@Stable
class GuideAnchors {
    private val bounds = mutableStateMapOf<GuideStep, Rect>()

    operator fun get(step: GuideStep): Rect? = bounds[step]

    fun put(step: GuideStep, rect: Rect) {
        if (bounds[step] != rect) bounds[step] = rect
    }

    fun remove(step: GuideStep) {
        bounds.remove(step)
    }
}
