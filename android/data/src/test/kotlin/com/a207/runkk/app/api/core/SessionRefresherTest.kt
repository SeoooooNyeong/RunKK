package com.a207.runkk.app.api.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.data.session.EncryptedTokenStore
import com.a207.runkk.app.domain.account.SessionTokens
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SessionRefresher·TokenAuthenticator 게스트 세션 재개(TC-6451~TC-6456, D-89, D-110, D-159 (4)).
 * 서버는 MockWebServer이고 경로별 응답을 [replies]로 정한다. 토큰 저장소는 실제 EncryptedTokenStore(일반 SharedPreferences)다.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRefresherTest {
    private val server = MockWebServer()
    private lateinit var store: EncryptedTokenStore
    private lateinit var refresher: SessionRefresher
    private lateinit var client: OkHttpClient

    /** 경로 → 응답. 원 요청(/api/v1/runs)은 "Bearer access-1"이면 401, 그 밖이면 200이다. */
    private val replies = mutableMapOf<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @BeforeTest
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath
                    replies[path]?.let { return it() }
                    val expired = request.headers["Authorization"] == "Bearer access-1"
                    return MockResponse.Builder().code(if (expired) 401 else 200).body("ok").build()
                }
            }
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("refresher-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = EncryptedTokenStore { prefs }
        val base = OkHttpClient()
        refresher = SessionRefresher(store, base, server.url(REFRESH), ApiCoreBindings.json())
        client = ApiCoreBindings.authenticatedClient(base, store, refresher)
    }

    @AfterTest
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200): MockResponse =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun status(code: Int): MockResponse = MockResponse.Builder().code(code).build()

    private fun callRuns(): Int =
        client.newCall(Request.Builder().url(server.url("/api/v1/runs")).build()).execute().use { it.code }

    private fun paths(path: String): List<RecordedRequest> = requests.filter { it.url.encodedPath == path }

    @Test
    fun `TC-6451 세 곳이 동시에 갱신을 불러도 POST auth refresh는 한 번이고 같은 결과를 받는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        val arrived = CountDownLatch(1)
        replies[REFRESH] = {
            arrived.countDown()
            Thread.sleep(SLOW_MILLIS)
            json("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
        }
        val pool = Executors.newFixedThreadPool(3)
        val rest1 = pool.submit<Int> { callRuns() }
        val rest2 = pool.submit<Int> { callRuns() }
        val live = pool.submit<SessionRefresher.Refresh> { refresher.refreshNow() }

        assertEquals(200, rest1.get(5, TimeUnit.SECONDS))
        assertEquals(200, rest2.get(5, TimeUnit.SECONDS))
        assertEquals(SessionRefresher.Refresh.Renewed("access-2"), live.get(5, TimeUnit.SECONDS))
        pool.shutdown()
        assertTrue(arrived.await(1, TimeUnit.SECONDS))
        assertEquals(1, paths(REFRESH).size, "갱신은 한 곳에서 한 번")
        assertEquals("access-2", store.accessToken())
    }

    @Test
    fun `TC-6452 갱신이 거부되고 게스트 자격 증명이 있으면 게스트 세션을 다시 열어 원 요청을 다시 보낸다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        store.saveGuestCredential("g1.42.secret")
        replies[REFRESH] = { status(401) }
        replies[GUEST] = { json("""{"accessToken":"access-3","refreshToken":"refresh-3"}""") }

        assertEquals(200, callRuns())

        assertEquals(1, paths(GUEST).size)
        assertEquals("""{"guestCredential":"g1.42.secret"}""", paths(GUEST).single().body?.utf8())
        assertNull(paths(GUEST).single().headers["Authorization"], "재개 요청에 만료된 접근 토큰을 싣지 않는다")
        assertEquals("Bearer access-3", requests.last().headers["Authorization"])
        assertEquals("access-3", store.accessToken())
        assertEquals("refresh-3", store.refreshToken())
        assertTrue(store.loggedIn.value)
        assertTrue(store.hasGuestCredential())
    }

    @Test
    fun `TC-6453 게스트 세션 재개도 거부되면 한 번만 시도하고 로그아웃한다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        store.saveGuestCredential("g1.42.secret")
        replies[REFRESH] = { status(401) }
        replies[GUEST] = { status(401) }

        assertEquals(401, callRuns())

        assertEquals(1, paths(GUEST).size)
        assertFalse(store.loggedIn.value)
        assertNull(store.accessToken())
        assertFalse(store.hasGuestCredential(), "clear는 게스트 자격 증명도 지운다")
    }

    @Test
    fun `TC-6454 게스트 세션 재개가 일시 오류면 토큰과 자격 증명을 남긴다`() = runTest {
        for (failure in listOf({ status(503) }, { status(429) }, { disconnect() })) {
            requests.clear()
            store.save(SessionTokens("access-1", "refresh-1"))
            store.saveGuestCredential("g1.42.secret")
            replies[REFRESH] = { status(401) }
            replies[GUEST] = failure

            assertEquals(401, callRuns())

            assertEquals("refresh-1", store.refreshToken())
            assertTrue(store.loggedIn.value)
            assertTrue(store.hasGuestCredential())
        }
    }

    @Test
    fun `TC-6455 갱신이 거부되고 자격 증명이 없으면 재개하지 않고 로그아웃한다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        replies[REFRESH] = { status(403) }

        assertEquals(SessionRefresher.Refresh.Rejected, refresher.refreshNow())

        assertEquals(0, paths(GUEST).size)
        assertFalse(store.loggedIn.value)
    }

    @Test
    fun `TC-6456 갱신이 일시 오류면 게스트 세션을 재개하지 않고 토큰을 남긴다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        store.saveGuestCredential("g1.42.secret")
        replies[REFRESH] = { status(503) }

        assertEquals(SessionRefresher.Refresh.Unavailable, refresher.refreshNow())

        assertEquals(0, paths(GUEST).size)
        assertTrue(store.loggedIn.value)
        assertEquals("refresh-1", store.refreshToken())
    }

    @Test
    fun `갱신 토큰이 없으면 서버를 부르지 않고 거부로 본다`() {
        assertEquals(SessionRefresher.Refresh.Rejected, refresher.refreshNow())
        assertEquals(0, requests.size)
    }

    @Test
    fun `AN5-C-8 갱신 응답을 받은 직후 로그아웃이 끼어도 새 토큰을 저장하지 않고 로그아웃이 남는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        val racing = InterleaveOnNextUse(store) { store.clear() }
        val refresher = SessionRefresher(racing, OkHttpClient(), server.url(REFRESH), ApiCoreBindings.json())
        replies[REFRESH] = {
            racing.isArmed = true
            json("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
        }

        val result = refresher.refreshNow()

        assertEquals(SessionRefresher.Refresh.Rejected, result)
        assertFalse(store.loggedIn.value, "로그아웃이 되살아나지 않는다")
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    @Test
    fun `AN5-C-8 게스트 재개 응답을 받은 직후 로그아웃이 끼어도 새 토큰을 저장하지 않는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        store.saveGuestCredential("g1.42.secret")
        val racing = InterleaveOnNextUse(store) { store.clear() }
        val refresher = SessionRefresher(racing, OkHttpClient(), server.url(REFRESH), ApiCoreBindings.json())
        replies[REFRESH] = { status(401) }
        replies[GUEST] = {
            racing.isArmed = true
            json("""{"accessToken":"access-3","refreshToken":"refresh-3"}""")
        }

        val result = refresher.refreshNow()

        assertEquals(SessionRefresher.Refresh.Rejected, result)
        assertFalse(store.loggedIn.value)
        assertNull(store.accessToken())
    }

    @Test
    fun `AN6-2 갱신 응답을 받은 직후 새 로그인이 토큰을 바꾸면 늦은 갱신 결과가 새 토큰을 덮어쓰지 않는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        val racing = InterleaveOnNextUse(store) { store.save(SessionTokens("login-access", "login-refresh")) }
        val refresher = SessionRefresher(racing, OkHttpClient(), server.url(REFRESH), ApiCoreBindings.json())
        replies[REFRESH] = {
            racing.isArmed = true
            json("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
        }

        val result = refresher.refreshNow()

        assertEquals(SessionRefresher.Refresh.Rejected, result)
        assertEquals("login-access", store.accessToken())
        assertEquals("login-refresh", store.refreshToken())
        assertTrue(store.loggedIn.value, "새 로그인은 그대로 남는다")
    }

    @Test
    fun `AN6-2 게스트 재개 응답을 받은 직후 연동 로그인이 토큰을 바꾸면 재개 결과가 덮어쓰지 않는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        store.saveGuestCredential("g1.42.secret")
        val racing = InterleaveOnNextUse(store) { store.save(SessionTokens("linked-access", "linked-refresh")) }
        val refresher = SessionRefresher(racing, OkHttpClient(), server.url(REFRESH), ApiCoreBindings.json())
        replies[REFRESH] = { status(401) }
        replies[GUEST] = {
            racing.isArmed = true
            json("""{"accessToken":"access-3","refreshToken":"refresh-3"}""")
        }

        val result = refresher.refreshNow()

        assertEquals(SessionRefresher.Refresh.Rejected, result)
        assertEquals("linked-access", store.accessToken())
        assertEquals("linked-refresh", store.refreshToken())
        assertTrue(store.loggedIn.value)
    }

    /**
     * 갱신 응답이 온 뒤([isArmed]) 저장소를 처음 쓰는 순간, 그 호출이 값을 읽은 바로 뒤에 다른 스레드의 [interleave](로그아웃 clear()나
     * 새 로그인 save())가 끼어든 것처럼 부른다. 확인한 뒤 저장(check-then-act)하는 구현은 확인을 통과하고 저장해 로그아웃을 되살리거나
     * 새 로그인의 토큰을 덮어쓴다.
     */
    private class InterleaveOnNextUse(
        private val store: EncryptedTokenStore,
        private val interleave: suspend () -> Unit
    ) : TokenProvider by store {
        @Volatile
        var isArmed = false

        override fun refreshToken(): String? = store.refreshToken().also { interleaveIfArmed() }

        override fun guestCredential(): String? = store.guestCredential().also { interleaveIfArmed() }

        override fun sessionGeneration(): Long = store.sessionGeneration().also { interleaveIfArmed() }

        override fun updateInSession(generation: Long, access: String, refresh: String): Boolean {
            interleaveIfArmed()
            return store.updateInSession(generation, access, refresh)
        }

        private fun interleaveIfArmed() {
            if (!isArmed) return
            isArmed = false
            runBlocking { interleave() }
        }
    }

    private fun disconnect(): MockResponse = MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()

    private companion object {
        const val REFRESH = "/api/v1/auth/refresh"
        const val GUEST = "/api/v1/auth/guest/session"
        const val SLOW_MILLIS = 300L
    }
}
