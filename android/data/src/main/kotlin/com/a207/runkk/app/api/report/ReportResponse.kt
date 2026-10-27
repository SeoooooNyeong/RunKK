package com.a207.runkk.app.api.report

import kotlinx.serialization.Serializable

/** POST /reports 201 응답(서버 ReportController.ReportResponse). status는 접수 뒤 사례 상태(RECEIVED 또는 AUTO_HIDDEN)다. */
@Serializable
internal data class ReportResponse(val reportId: Long, val status: String)
