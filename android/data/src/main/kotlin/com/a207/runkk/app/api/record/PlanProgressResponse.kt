package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanProgressResponse(val days: List<Day> = emptyList()) {
    @Serializable
    data class Day(
        val date: String,
        val plannedMeters: Long? = null,
        val doneMeters: Long = 0,
        val supplementMeters: Long? = null
    )
}
