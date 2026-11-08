package com.a207.runkk.app.api.runbody

import kotlinx.serialization.Serializable

/** GET /runbody/sessions(계약 v1.2 7절). 최신순, [nextCursor]는 다음 쪽 커서(앱은 첫 쪽만 읽는다). */
@Serializable
internal data class RunBodySessionListResponse(val items: List<Row> = emptyList(), val nextCursor: String? = null) {
    @Serializable
    data class Row(val recordId: String, val status: String, val score: Int? = null, val scoreChange: Int? = null)
}
