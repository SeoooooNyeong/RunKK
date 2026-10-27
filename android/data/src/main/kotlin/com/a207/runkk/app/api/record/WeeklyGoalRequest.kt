package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class WeeklyGoalRequest(val runs: Int, val distanceMeters: Long)
