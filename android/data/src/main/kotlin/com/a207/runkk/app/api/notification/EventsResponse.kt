package com.a207.runkk.app.api.notification

import kotlinx.serialization.Serializable

/** 서버 GET /events 응답(이벤트 계약 2절). 세 URL은 서버 주소 기준 상대 경로다. */
@Serializable
internal data class EventsResponse(val events: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val id: Long,
        val slug: String,
        val title: String,
        val subtitle: String? = null,
        val status: String,
        val startsAt: String,
        val endsAt: String,
        val bannerImageUrl: String? = null,
        val thumbnailImageUrl: String? = null,
        val detailUrl: String? = null,
        val entryCount: Int = 0,
        val maxEntryCount: Int = 0,
        val sortOrder: Int = 0
    )
}
