package com.a207.runkk.app.api.core

import com.a207.runkk.app.api.core.AccessTokenExpiryTest.Companion.jwt
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 토큰 미리 갱신 스레드(Jira 326, MockWebServer): AuthInterceptor가 접근 토큰 exp − 서버 시각 < 60초면 원 요청 전에 갱신을 한 번 하고
 * 새 토큰으로 보낸다(401 0건). 여러 요청이 함께 와도 갱신은 한 번이다. exp를 못 읽거나 갱신이 네트워크로 실패하면 기존 토큰으로 보내고
 * 401은 기존 TokenAuthenticator 경로다.
 */
class ProactiveRefreshTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-11-18T00:00:00Z")
    private val clock = ServerClock(Clock.fixed(now, ZoneOffset.UTC))
    private val near = jwt("""{"sub":"7","exp":${now.epochSecond + 30}}""")
    private val far = jwt("""{"sub":"7","exp":${now.epochSecond + 3600}}""")
    private val renewed = jwt("""{"sub":"7","exp":${now.epochSecond + 3600},"n":2}""")
    private val tokens = Tokens(near)
    private val seen = ConcurrentLinkedQueue<String>()

    /** 갱신 응답(기본: 새 토큰). */
    @Volatile
    private var refreshReply: () -> MockResponse = {
        MockResponse.Builder().code(200).body("""{"accessToken":"$renewed","refreshToken":"refresh-2"}""").build()
    }

    private val client: OkHttpClient by lazy {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                seen += path + " " + (request.headers["Authorization"] ?: "-")
                if (path.endsWith("/auth/refresh")) {
                    Thread.sleep(50)
                    return refreshReply()
                }
                val auth = request.headers["Authorization"]
                return if (auth == "Bearer $renewed" || auth == "Bearer $far") {
                    MockResponse.Builder().code(200).body("ok").build()
                } else {
                    MockResponse.Builder().code(401).build()
                }
            }
        }
        server.start()
        val base = OkHttpClient()
        val refresher = SessionRefresher(
            tokens,
            base,
            server.url("/api/v1/auth/refresh"),
            Json {
                ignoreUnknownKeys =
                    true
            }
        )
        ApiCoreBindings.authenticatedClient(base, tokens, refresher, clock)
    }

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `만료 60초 전이면 원 요청 전에 갱신을 한 번 하고 새 토큰으로 보낸다(401 0건)`() {
        val reply = call()
        assertEquals(200, reply)
        assertEquals(
            listOf("/api/v1/auth/refresh -", "/api/v1/dashboard Bearer $renewed"),
            seen.toList()
        )
    }

    @Test
    fun `여러 요청이 함께 와도 갱신은 한 번이고 모두 새 토큰으로 간다`() {
        val pool = Executors.newFixedThreadPool(4)
        val codes = (1..4).map { pool.submit<Int> { call() } }.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(listOf(200, 200, 200, 200), codes)
        assertEquals(1, seen.count { it.startsWith("/api/v1/auth/refresh") }, "$seen")
        assertTrue(seen.none { "401" in it })
        assertEquals(4, seen.count { it == "/api/v1/dashboard Bearer $renewed" }, "$seen")
    }

    @Test
    fun `만료가 멀면 갱신하지 않는다`() {
        tokens.access = far
        assertEquals(200, call())
        assertEquals(listOf("/api/v1/dashboard Bearer $far"), seen.toList())
    }

    @Test
    fun `exp를 못 읽으면 기존 토큰으로 보내고 401이면 기존 갱신 경로를 탄다`() {
        tokens.access = "opaque-token"
        assertEquals(200, call())
        assertEquals(
            listOf(
                "/api/v1/dashboard Bearer opaque-token",
                "/api/v1/auth/refresh -",
                "/api/v1/dashboard Bearer $renewed"
            ),
            seen.toList()
        )
    }

    @Test
    fun `미리 갱신이 일시 오류로 실패하면 막지 않고 기존 토큰으로 보낸다`() {
        refreshReply = { MockResponse.Builder().code(503).build() }
        assertEquals(401, call(), "기존 토큰이 거부되면 401 경로(TokenAuthenticator도 같은 503이라 원 요청 실패)")
        assertEquals("/api/v1/auth/refresh -", seen.first())
        assertTrue(seen.any { it == "/api/v1/dashboard Bearer $near" }, "$seen")
        assertEquals("refresh-1", tokens.refreshToken(), "일시 오류는 토큰을 지우지 않는다")
    }

    private fun call(): Int =
        client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute().use { it.code }

    private class Tokens(@Volatile var access: String?) : TokenProvider {
        @Volatile
        private var refresh: String? = "refresh-1"

        override fun accessToken(): String? = access

        override fun refreshToken(): String? = refresh

        @Synchronized
        override fun update(access: String, refresh: String) {
            this.access = access
            this.refresh = refresh
        }

        override suspend fun clear(): Outcome<Unit> {
            access = null
            refresh = null
            return Outcome.Success(Unit)
        }
    }
}
