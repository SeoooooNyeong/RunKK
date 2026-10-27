package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class CrewRankingResponse(val entries: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val rank: Int, val displayName: String? = null, val distanceMeters: Long)
}
