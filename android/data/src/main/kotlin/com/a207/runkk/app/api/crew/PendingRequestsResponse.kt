package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class PendingRequestsResponse(val requests: List<Request> = emptyList()) {
    @Serializable
    data class Request(
        val requestId: Long,
        val memberId: Long,
        val displayName: String? = null,
        val requestedAt: String,
        val tier: String? = null,
        val level: Int? = null,
        val ageBand: String? = null,
        val gender: String? = null
    )
}
