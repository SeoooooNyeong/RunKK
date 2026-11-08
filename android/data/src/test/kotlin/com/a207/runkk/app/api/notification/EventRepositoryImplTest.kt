package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventEntry
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.create

/** api.notification 이벤트 매퍼와 저장소(이벤트 계약 2절): 상대 경로를 API origin에 붙이고, 본 이벤트 사본을 설정 사본에 맡긴다. */
class EventRepositoryImplTest {
    private val api = ApiTestServer()
    private val settings = SeenEventSettings()
    private val repo by lazy {
        EventRepositoryImpl(api.retrofit.create(), Tokens, settings, api.server.url("/api/v1/"))
    }
    private val origin: String get() = api.server.url("/").toString().trimEnd('/')

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `목록의 상대 경로를 API origin에 붙이고 모르는 상태는 뺀다`() = runTest {
        api.reply(
            """{"events":[
              |{"id":1,"slug":"coach-challenge","title":"코치님을 이겨라","subtitle":"GS 상품권","status":"ONGOING",
              | "startsAt":"2026-11-08T00:00:00","endsAt":"2026-11-16T23:59:59",
              | "bannerImageUrl":"/events/coach-challenge/banner.png","thumbnailImageUrl":"/events/coach-challenge/thumb.png",
              | "detailUrl":"/events/coach-challenge/","entryCount":1,"maxEntryCount":2,"sortOrder":1},
              |{"id":2,"slug":"next","title":"새 상태","status":"PAUSED","startsAt":"2026-11-08T00:00:00","endsAt":"2026-11-16T23:59:59"}
              |]}
            """.trimMargin()
        )

        val events = repo.events().value()

        assertEquals("/api/v1/events", api.take().url.encodedPath)
        assertEquals(
            listOf(
                Event(
                    id = 1,
                    slug = "coach-challenge",
                    title = "코치님을 이겨라",
                    subtitle = "GS 상품권",
                    status = Event.Status.ONGOING,
                    startsAt = Instant.parse("2026-11-07T15:00:00Z"),
                    endsAt = Instant.parse("2026-11-16T14:59:59Z"),
                    bannerImageUrl = "$origin/events/coach-challenge/banner.png",
                    thumbnailImageUrl = "$origin/events/coach-challenge/thumb.png",
                    detailUrl = "$origin/events/coach-challenge/",
                    entryCount = 1,
                    maxEntryCount = 2,
                    sortOrder = 1
                )
            ),
            events
        )
    }

    @Test
    fun `절대 주소와 다른 호스트 경로는 받지 않는다`() {
        val base = "https://k15a207.p.ssafy.io:8443/api/v1/".toHttpUrl()
        assertEquals("https://k15a207.p.ssafy.io:8443/events/a/", resolveEventPath(base, "/events/a/"))
        assertNull(resolveEventPath(base, "https://evil.example/events/a/"))
        assertNull(resolveEventPath(base, "//evil.example/events/a/"))
        assertNull(resolveEventPath(base, "events/a/"))
        assertNull(resolveEventPath(base, "/\\evil.example"))
        assertNull(resolveEventPath(base, null))
        assertEquals("https://k15a207.p.ssafy.io", originOf("https://k15a207.p.ssafy.io/api/v1/".toHttpUrl()))
        assertEquals("http://10.0.2.2:8080", originOf("http://10.0.2.2:8080/api/v1/".toHttpUrl()))
    }

    @Test
    fun `내 참여를 읽는다`() = runTest {
        api.reply(
            """{"slug":"beta-1km","entryCount":1,"maxEntryCount":1,
              |"items":[{"key":"run-1km","label":"1km","done":true,"doneAt":"2026-11-09T19:02:11","distanceMeters":1043}]}
            """.trimMargin()
        )

        val entry = repo.me("beta-1km").value()

        assertEquals("/api/v1/events/beta-1km/me", api.take().url.encodedPath)
        assertEquals(
            EventEntry(
                "beta-1km",
                1,
                1,
                listOf(EventEntry.Item("run-1km", "1km", true, Instant.parse("2026-11-09T10:02:11Z"), 1043))
            ),
            entry
        )
    }

    @Test
    fun `본 이벤트는 지금 목록에 있는 것만 남기고 더한다`() = runTest {
        settings.ids = setOf(1L, 9L)
        assertEquals(Outcome.Success(Unit), repo.markSeen(setOf(2L), current = setOf(1L, 2L, 3L)))
        assertEquals(setOf(1L, 2L), repo.seenEventIds())
    }

    @Test
    fun `API origin 밖 그림 주소는 받지 않는다`() = runTest {
        assertEquals(
            Outcome.Failure(AppError.Rejected("NOT_EVENT_IMAGE")),
            repo.image("https://evil.example/events/a/thumb.png")
        )
        assertEquals("$origin", repo.webOrigin())
        assertEquals("access-1", repo.accessToken())
    }

    private object Tokens : TokenProvider {
        override fun accessToken(): String = "access-1"

        override fun refreshToken(): String = "refresh-1"

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

/** 본 이벤트 사본만 쓰는 설정 저장소 가짜. 나머지는 사본 없음 값이다. */
private class SeenEventSettings : SettingsRepository {
    var ids: Set<Long> = emptySet()

    override suspend fun seenEventIds(): Set<Long> = ids

    override suspend fun saveSeenEventIds(ids: Set<Long>): Outcome<Unit> {
        this.ids = ids
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = false

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun isGuest() = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)
}
