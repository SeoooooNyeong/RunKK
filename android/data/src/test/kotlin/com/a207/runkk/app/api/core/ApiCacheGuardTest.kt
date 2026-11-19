package com.a207.runkk.app.api.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 일반 API 디스크 캐시(Jira 328): 서버가 Cache-Control을 준 응답(크루 로고 등)은 디스크에 남아 다시 받지 않는다. 계정별 칸으로 나눠
 * 다른 계정·로그아웃에는 앞 계정 응답이 나가지 않고, 비우지 않으므로 앞 계정으로 돌아오면 그 칸을 그대로 쓴다(S15P31A207-345 2차).
 */
class ApiCacheGuardTest {
    private val server = MockWebServer().apply { start() }
    private val dir = kotlin.io.path.createTempDirectory("api-http").toFile()
    private val cache = Cache(dir, 1024L * 1024)

    @Volatile
    private var member: Long? = 1
    private val client = newClient(cache)

    private fun newClient(cache: Cache) =
        OkHttpClient.Builder().cache(cache).addInterceptor(ApiCacheGuard { member }).build()

    @AfterTest
    fun tearDown() {
        server.close()
        cache.close()
        dir.deleteRecursively()
    }

    @Test
    fun `캐시 머리글이 있는 응답은 디스크에서 다시 주고, 회원이 바뀌거나 로그아웃하면 그 칸에 없어 다시 받는다`() {
        repeat(4) {
            server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("logo").build())
        }

        get()
        get()
        assertEquals(1, server.requestCount, "두 번째는 디스크 캐시")

        member = 2
        get()
        assertEquals(2, server.requestCount, "회원이 바뀌면 그 회원 칸에 없어 다시 받는다")

        member = null
        get()
        assertEquals(3, server.requestCount, "로그아웃 칸에도 없어 다시 받는다")
    }

    @Test
    fun `앱을 새로 켜도 같은 회원이면 디스크 캐시를 그대로 쓴다`() {
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("logo").build())
        get()
        cache.close()

        val reopened = Cache(dir, 1024L * 1024)
        newClient(reopened).newCall(Request.Builder().url(server.url("/api/v1/crews/1/logo")).build()).execute().close()
        assertEquals(1, server.requestCount, "재시작 뒤에도 서버에 묻지 않는다")
        reopened.close()
    }

    @Test
    fun `앞 회원 요청이 도는 사이 회원이 바뀌어도 늦게 도착한 앞 회원 응답은 앞 회원 칸에만 남는다`() {
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("B").build())
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("A").build())
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("B2").build())
        lateinit var racing: OkHttpClient
        racing = OkHttpClient.Builder().cache(cache).addInterceptor(ApiCacheGuard { member })
            .addNetworkInterceptor { chain ->
                if (chain.request().header("X-Race") != null) {
                    member = 2
                    val other = Request.Builder().url(server.url("/api/v1/crews/1/logo")).build()
                    racing.newCall(other).execute().use { it.body.string() }
                }
                chain.proceed(chain.request())
            }.build()

        val raced = Request.Builder().url(server.url("/api/v1/crews/1/logo")).header("X-Race", "1").build()
        assertEquals("A", racing.newCall(raced).execute().use { it.body.string() })
        val next = Request.Builder().url(server.url("/api/v1/crews/1/logo")).build()

        assertEquals("B", racing.newCall(next).execute().use { it.body.string() }, "회원 1의 응답을 회원 2에게 주지 않는다")
        assertEquals(2, server.requestCount, "회원 2는 자기 칸의 캐시를 쓴다")
        member = 1
        assertEquals("A", racing.newCall(next).execute().use { it.body.string() }, "늦게 온 응답은 회원 1 칸에 남는다")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `다른 계정으로 로그인했다 돌아오면 앞 계정 칸의 캐시를 지우지 않고 그대로 쓴다`() {
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("A").build())
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=3600").body("B").build())

        assertEquals("A", get())
        member = 2
        assertEquals("B", get(), "다른 계정은 앞 계정 응답을 받지 않는다")
        member = null
        member = 1
        assertEquals("A", get(), "돌아온 계정은 자기 칸의 캐시를 그대로 쓴다")
        member = 2
        assertEquals("B", get())
        assertEquals(2, server.requestCount, "칸마다 한 번씩만 받는다")
    }

    @Test
    fun `캐시 머리글이 없는 응답은 남기지 않는다`() {
        repeat(2) { server.enqueue(MockResponse.Builder().body("me").build()) }
        get()
        get()
        assertEquals(2, server.requestCount)
    }

    private fun get() =
        client.newCall(Request.Builder().url(server.url("/api/v1/crews/1/logo")).build()).execute().use {
            it.body.string()
        }
}
