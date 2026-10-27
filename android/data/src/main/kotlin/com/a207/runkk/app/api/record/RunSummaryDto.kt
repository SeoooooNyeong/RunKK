package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class RunSummaryDto(
    val recordId: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val valid: Boolean,
    val mode: String,
    val cardKind: String
)
