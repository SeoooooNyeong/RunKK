package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class TodayPlanResponse(val distanceMeters: Long? = null, val paceSecondsPerKm: Int? = null)
