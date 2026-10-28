package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
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
    val goalDistance: Distance? = null
) {
    /** 서버는 횟수·거리를 둘 다 받아야 한다(PUT /goals/weekly, 없으면 400). */
    val canSaveGoal: Boolean get() = goalRuns != null && goalDistance != null && !isSaving

    companion object {
        /** 주간 목표 횟수 범위(FR-072). */
        val GOAL_RUNS: IntRange = 1..7
    }
}
