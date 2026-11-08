package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventEntry
import okhttp3.HttpUrl

/** GET /events → 이벤트 목록. 모르는 상태 값(새 서버의 새 값)인 이벤트는 뺀다. 세 URL은 [origin]에 붙인다([resolveEventPath]). */
internal fun EventsResponse.toDomain(origin: HttpUrl): List<Event> = events.mapNotNull { item ->
    val status = Event.Status.entries.firstOrNull { it.name == item.status } ?: return@mapNotNull null
    Event(
        id = item.id,
        slug = item.slug,
        title = item.title,
        subtitle = item.subtitle?.takeIf { it.isNotBlank() },
        status = status,
        startsAt = ServerTime.parse(item.startsAt),
        endsAt = ServerTime.parse(item.endsAt),
        bannerImageUrl = resolveEventPath(origin, item.bannerImageUrl),
        thumbnailImageUrl = resolveEventPath(origin, item.thumbnailImageUrl),
        detailUrl = resolveEventPath(origin, item.detailUrl),
        entryCount = item.entryCount,
        maxEntryCount = item.maxEntryCount,
        sortOrder = item.sortOrder
    )
}

internal fun EventEntryResponse.toDomain(): EventEntry = EventEntry(
    slug = slug,
    entryCount = entryCount,
    maxEntryCount = maxEntryCount,
    items = items.map {
        EventEntry.Item(
            key = it.key,
            label = it.label,
            isDone = it.done,
            doneAt = it.doneAt?.let(ServerTime::parse),
            distanceMeters = it.distanceMeters
        )
    }
)

/**
 * 서버 상대 경로("/events/coach-challenge/")를 API origin에 붙인다. "/"로 시작하는 경로만 받는다: 절대 주소("https://…")와
 * 다른 호스트를 가리키는 "//host/…"는 null이다(웹뷰가 API origin 밖을 열지 않게).
 */
internal fun resolveEventPath(origin: HttpUrl, path: String?): String? {
    if (path.isNullOrBlank() || !path.startsWith("/") || path.startsWith("//") || path.contains('\\')) return null
    val resolved = origin.resolve(path) ?: return null
    return resolved.toString().takeIf { originOf(resolved) == originOf(origin) }
}

/** scheme://host[:port](기본 포트는 뺀다). */
internal fun originOf(url: HttpUrl): String {
    val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
    return "${url.scheme}://${url.host}$port"
}
