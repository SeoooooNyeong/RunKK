package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanRequest(val distanceMeters: Long, val paceSecondsPerKm: Int?)
