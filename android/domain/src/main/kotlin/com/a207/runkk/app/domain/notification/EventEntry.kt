package com.a207.runkk.app.domain.notification

import java.time.Instant

/** 내 이벤트 참여(GET /events/{slug}/me, 이벤트 계약 2절): 응모 수와 조건별 달성 여부. */
data class EventEntry(val slug: String, val entryCount: Int, val maxEntryCount: Int, val items: List<Item>) {
    data class Item(
        val key: String,
        val label: String,
        val isDone: Boolean,
        val doneAt: Instant?,
        val distanceMeters: Long? = null
    )
}
