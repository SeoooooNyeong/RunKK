package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.BuildConfig
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventEntry
import com.a207.runkk.app.domain.notification.EventRepository
import com.a207.runkk.app.domain.settings.SettingsRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 이벤트 포트(GET /events, GET /events/{slug}/me, 이벤트 그림)를 구현한다(이벤트 계약 2절, 5절). 그림·상세 주소는 API 기준 주소
 * ([baseUrl])의 origin에 붙인다. 본 이벤트 ID는 설정 사본(SettingsRepository)에 맡긴다(배치고사 완료 사본과 같은 방식).
 * 그림은 몇 장 안 되므로 받은 바이트를 메모리에 둔다(배너를 넘겼다 돌아와도 다시 받지 않게).
 */
@Singleton
internal class EventRepositoryImpl internal constructor(
    private val api: EventApi,
    private val tokens: TokenProvider,
    private val settings: SettingsRepository,
    baseUrl: HttpUrl
) : EventRepository {
    @Inject
    constructor(api: EventApi, tokens: TokenProvider, settings: SettingsRepository) :
        this(api, tokens, settings, BuildConfig.API_BASE_URL.toHttpUrl())

    private val origin: HttpUrl = baseUrl.newBuilder().encodedPath("/").query(null).fragment(null).build()

    private val images = object : LinkedHashMap<String, ByteArray>(IMAGE_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > IMAGE_ENTRIES
    }

    override suspend fun events(): Outcome<List<Event>> = ApiErrorMapper.call { api.events().toDomain(origin) }

    override suspend fun me(slug: String): Outcome<EventEntry> = ApiErrorMapper.call { api.me(slug).toDomain() }

    override suspend fun image(url: String): Outcome<ByteArray> {
        synchronized(images) { images[url] }?.let { return Outcome.Success(it) }
        // 이 포트가 만든 주소(API origin)만 받는다.
        if (url.toHttpUrlOrNull()?.let(::originOf) != originOf(origin)) {
            return Outcome.Failure(AppError.Rejected(NOT_EVENT_IMAGE))
        }
        val read = ApiErrorMapper.call {
            api.image(url).use { body ->
                withContext(Dispatchers.IO) { body.byteStream().use { it.readAtMost(MAX_IMAGE_BYTES + 1) } }
            }
        }
        if (read !is Outcome.Success) return read
        if (read.value.size > MAX_IMAGE_BYTES) return Outcome.Failure(AppError.Rejected(TOO_LARGE))
        synchronized(images) { images[url] = read.value }
        return read
    }

    override fun webOrigin(): String = originOf(origin)

    override fun accessToken(): String? = tokens.accessToken()

    override suspend fun seenEventIds(): Set<Long> = settings.seenEventIds()

    override suspend fun markSeen(ids: Set<Long>, current: Set<Long>): Outcome<Unit> {
        val seen = settings.seenEventIds()
        val next = (seen intersect current) + ids
        return if (next == seen) Outcome.Success(Unit) else settings.saveSeenEventIds(next)
    }

    private companion object {
        const val IMAGE_ENTRIES = 16
        const val LOAD_FACTOR = 0.75f

        /** 이벤트 그림 한 장의 상한(3MiB). */
        const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
        const val TOO_LARGE = "TOO_LARGE"
        const val NOT_EVENT_IMAGE = "NOT_EVENT_IMAGE"
    }
}

/** 최대 [limit]바이트까지 읽는다(InputStream.readNBytes는 API 33부터라 직접 센다). */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private const val BUFFER_BYTES = 8 * 1024
