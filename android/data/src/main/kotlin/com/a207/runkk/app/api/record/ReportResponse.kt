package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class ReportResponse(
    val summary: String? = null,
    val comment: String? = null,
    val commentSource: String? = null,
    val message: String? = null
)
