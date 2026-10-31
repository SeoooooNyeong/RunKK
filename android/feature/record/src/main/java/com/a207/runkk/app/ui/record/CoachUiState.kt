package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.PlanProgress
import java.time.LocalDate

data class CoachUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val isLoading: Boolean = true,
    val progress: PlanProgress? = null,
    val isSaving: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
    /** 고른 주간 목표 횟수(1~7). */
    val goalRuns: Int? = null,
    /** 고른 주간 목표 거리. */
    val goalDistance: Distance? = null,
    /**
     * 오늘 계획의 목표 페이스(GET /plans/today). 오늘 계획 거리가 없을 때 고르면 거리를 고를 때 함께 저장한다. 없으면 페이스 안내 없이
     * 거리만 따른다(코치 계획 따르기, FR-044).
     */
    val todayPace: Pace? = null
) {
    /** 서버는 횟수·거리를 둘 다 받아야 한다(PUT /goals/weekly, 없으면 400). */
    val canSaveGoal: Boolean get() = goalRuns != null && goalDistance != null && !isSaving

    companion object {
        /** 주간 목표 횟수 범위(FR-072). */
        val GOAL_RUNS: IntRange = 1..7
    }
}
