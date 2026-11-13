package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/{id}/goals/history(서버 CrewGoalHistoryController.GoalHistoryResponse, contract-W7 4장). */
@Serializable
internal data class GoalHistoryResponse(val goals: List<Goal> = emptyList(), val nextBefore: String? = null) {
    @Serializable
    data class Goal(
        val weekStart: String,
        val month: Int,
        val weekOfMonth: Int,
        val goalMeters: Long,
        val totalMeters: Long,
        val achieved: Boolean,
        val progressPercent: Int,
        val participantCount: Int
    )
}
