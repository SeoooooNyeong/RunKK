package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class RecordListResponse(val runs: List<RunSummaryDto> = emptyList())
