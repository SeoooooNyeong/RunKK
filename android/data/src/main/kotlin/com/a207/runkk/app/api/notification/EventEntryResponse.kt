package com.a207.runkk.app.api.notification

import kotlinx.serialization.Serializable

/** 서버 GET /events/{slug}/me 응답(이벤트 계약 2절). */
@Serializable
internal data class EventEntryResponse(
    val slug: String,
    val entryCount: Int = 0,
    val maxEntryCount: Int = 0,
    val items: List<Item> = emptyList()
) {
    @Serializable
    data class Item(
        val key: String,
        val label: String,
        val done: Boolean = false,
        val doneAt: String? = null,
        val distanceMeters: Long? = null
    )
}
