package com.a207.runkk.app.api.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.data.session.EncryptedTokenStore
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.notification.LoginState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TokenAuthenticator(14장 단위(앱 데이터)): 401 → 갱신 1회 → 재요청, 갱신 실패 → TokenProvider.clear →
 * SessionRepository.loggedIn·LoginState.loggedIn 둘 다 거짓(design-review.md H-25). 서버는 MockWebServer다.
 */
@RunWith(RobolectricTestRunner::class)
class TokenAuthenticatorTest {
    private val server = MockWebServer()
    private lateinit var store: EncryptedTokenStore
    private lateinit var client: OkHttpClient

    @BeforeTest
    fun setUp() {
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("tokens-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = EncryptedTokenStore { prefs }
        client =
            ApiCoreBindings.authenticatedClient(
                base = OkHttpClient(),
                tokens = store,
                refreshUrl = server.url("/api/v1/auth/refresh"),
                json = Json { ignoreUnknownKeys = true }
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `401이면 갱신을 한 번 부르고 새 접근 토큰으로 다시 보낸다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"accessToken":"access-2","refreshToken":"refresh-2"}""").build()
        )
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val reply = client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute()

        assertEquals(200, reply.code)
        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("Bearer access-1", first.headers["Authorization"])
        assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
        assertNull(refresh.headers["Authorization"], "갱신 요청에는 만료된 접근 토큰을 싣지 않는다")
        assertEquals("""{"refreshToken":"refresh-1"}""", refresh.body?.utf8())
        assertEquals("Bearer access-2", retried.headers["Authorization"])
        assertEquals("access-2", store.accessToken())
        assertEquals("refresh-2", store.refreshToken())
        assertTrue(store.loggedIn.value)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `갱신이 거부되면 토큰을 지우고 두 로그인 상태가 모두 거짓이 된다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        val session: SessionRepository = store
        val loginState: LoginState = store
        assertTrue(session.loggedIn.value)
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"code":"AUTH_FAILED"}""").build())

        val reply = client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute()

        assertEquals(401, reply.code)
        assertEquals(2, server.requestCount, "갱신은 한 번만 부른다")
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertFalse(session.loggedIn.value)
        assertFalse(loginState.loggedIn.value)
    }

    @Test
    fun `새 토큰으로 다시 보낸 요청도 401이면 더 갱신하지 않는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"accessToken":"access-2","refreshToken":"refresh-2"}""").build()
        )
        server.enqueue(MockResponse.Builder().code(401).build())

        val reply = client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute()

        assertEquals(401, reply.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `토큰이 없으면 갱신하지 않는다`() {
        server.enqueue(MockResponse.Builder().code(401).build())

        val reply = client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute()

        assertEquals(401, reply.code)
        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `갱신 서버가 일시 오류(5xx·429)면 토큰을 지우지 않는다`() = runTest {
        store.save(SessionTokens("access-1", "refresh-1"))
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(503).build())

        val reply = client.newCall(Request.Builder().url(server.url("/api/v1/dashboard")).build()).execute()

        assertEquals(401, reply.code)
        assertEquals("refresh-1", store.refreshToken())
        assertTrue(store.loggedIn.value)
    }
}
