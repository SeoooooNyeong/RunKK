package com.a207.runkk.app.ui.notification

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventEntry
import com.a207.runkk.app.domain.notification.EventRepository
import java.time.Instant

/** 시험용 이벤트 저장소 가짜. 본 이벤트 사본은 메모리에 둔다. */
internal class FakeEventRepository(var result: Outcome<List<Event>> = Outcome.Success(sampleEvents)) : EventRepository {
    var seen: Set<Long> = emptySet()
    val meCalls = mutableListOf<String>()

    override suspend fun events(): Outcome<List<Event>> = result

    override suspend fun me(slug: String): Outcome<EventEntry> {
        meCalls += slug
        return Outcome.Success(EventEntry(slug, 0, 2, emptyList()))
    }

    override suspend fun image(url: String): Outcome<ByteArray> = Outcome.Failure(AppError.Network)

    override fun webOrigin(): String = ORIGIN

    override fun accessToken(): String = "access-1"

    override suspend fun seenEventIds(): Set<Long> = seen

    override suspend fun markSeen(ids: Set<Long>, current: Set<Long>): Outcome<Unit> {
        seen = (seen intersect current) + ids
        return Outcome.Success(Unit)
    }
}

internal const val ORIGIN = "https://k15a207.p.ssafy.io:8443"

private fun event(
    id: Long,
    slug: String,
    title: String,
    status: Event.Status,
    starts: String,
    ends: String,
    entry: Int = 0,
    max: Int = 0,
    subtitle: String? = null
) = Event(
    id = id,
    slug = slug,
    title = title,
    subtitle = subtitle,
    status = status,
    startsAt = Instant.parse(starts),
    endsAt = Instant.parse(ends),
    bannerImageUrl = "$ORIGIN/events/$slug/banner.png",
    thumbnailImageUrl = "$ORIGIN/events/$slug/thumb.png",
    detailUrl = "$ORIGIN/events/$slug/",
    entryCount = entry,
    maxEntryCount = max,
    sortOrder = id.toInt()
)

/** 계약 1절의 두 이벤트(코치님은 응모 없음, 1km는 응모 1회)와 곧 열림, 지난 이벤트 하나씩. 시각은 UTC(서울 -9시간). */
internal val sampleEvents: List<Event> = listOf(
    event(
        1,
        "coach-challenge",
        "코치님을 이겨라",
        Event.Status.ONGOING,
        "2026-11-07T15:00:00Z",
        "2026-11-16T14:59:59Z",
        max = 2,
        subtitle = "코치님 기록을 넘고 GS 상품권 받기"
    ),
    event(
        2, "beta-1km", "1km 걷거나 달리기", Event.Status.ONGOING, "2026-11-07T15:00:00Z", "2026-11-16T14:59:59Z",
        entry = 1, max = 1, subtitle = "1km 걷고 메가커피 쿠폰 받기"
    ),
    event(3, "update-1113", "11월 13일 업데이트", Event.Status.UPCOMING, "2026-11-12T15:00:00Z", "2026-11-20T14:59:59Z"),
    event(4, "beta-open", "베타 오픈 기념", Event.Status.ENDED, "2026-10-31T15:00:00Z", "2026-11-05T14:59:59Z")
)
