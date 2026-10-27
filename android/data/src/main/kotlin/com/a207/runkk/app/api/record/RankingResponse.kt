package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class RankingResponse(
    val entries: List<Entry> = emptyList(),
    val mine: Entry? = null,
    val gapToNextMeters: Long? = null,
    val aggregatedAt: String? = null,
    val notice: String? = null
) {
    @Serializable
    data class Entry(val rank: Int, val name: String, val distanceMeters: Long)
}
