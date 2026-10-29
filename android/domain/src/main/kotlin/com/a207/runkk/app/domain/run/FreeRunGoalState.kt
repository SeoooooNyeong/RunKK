package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance

/**
 * 자유런 목표까지의 상태(D-258, FR-224 진행 알림 "목표까지 n km"/"목표 달성"). 목표가 자유(없음·0)면 [None]이고 줄을 그리지 않는다.
 * 목표 거리에 정확히 닿으면 달성으로 본다(TC-9603 제안).
 */
sealed interface FreeRunGoalState {
    data object None : FreeRunGoalState

    data class Remaining(val distance: Distance) : FreeRunGoalState

    data object Reached : FreeRunGoalState

    companion object {
        fun of(target: Distance?, distance: Distance): FreeRunGoalState = when {
            target == null || target.meters <= 0 -> None
            distance >= target -> Reached
            else -> Remaining(target.minus(distance))
        }
    }
}
