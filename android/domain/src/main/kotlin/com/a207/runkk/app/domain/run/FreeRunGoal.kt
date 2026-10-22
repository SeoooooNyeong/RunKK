package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.settings.GuidanceSettings

/**
 * 자유런 한 회의 목표(FR-048 자유런 설정, FR-049 목표거리도달안내, FR-044 페이스 안내).
 * plan은 RunStartSpec.todayPlan에 실을 목표 거리·목표 페이스이고 null이면 목표 없이 달린다.
 * isFromCoachPlan은 오늘 계획을 따른 목표인지(홈 자유런 카드 문구가 쓴다).
 */
data class FreeRunGoal(val plan: PlanSnapshot?, val isFromCoachPlan: Boolean) {
    companion object {
        /** 목표 없이 달리기. */
        val NONE = FreeRunGoal(plan = null, isFromCoachPlan = false)

        /**
         * 코치 계획 따르기가 켜져 있고 오늘 계획(거리나 페이스)이 있으면 오늘 계획을 따른다. 계획에 거리가 없으면 거리만
         * 자유런 설정 목표로 채운다. 그 밖(따르기 꺼짐, 오늘 계획 없음)은 자유런 설정의 목표 거리이고 목표 페이스는 없다
         * (FR-048: 목표 페이스 안내는 코치 계획 따르기가 켜졌을 때만).
         */
        fun of(todayPlan: PlanSnapshot?, settings: GuidanceSettings): FreeRunGoal {
            val coach =
                todayPlan?.takeIf {
                    settings.shouldFollowCoachPlan && (it.targetDistance != null || it.targetPace != null)
                }
            if (coach != null) {
                return FreeRunGoal(
                    PlanSnapshot(coach.targetDistance ?: settings.freeRunTarget, coach.targetPace),
                    isFromCoachPlan = true
                )
            }
            val target = settings.freeRunTarget ?: return NONE
            return FreeRunGoal(PlanSnapshot(target, null), isFromCoachPlan = false)
        }
    }
}
