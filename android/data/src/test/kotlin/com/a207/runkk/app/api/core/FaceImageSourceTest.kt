package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer

/**
 * FaceImageSource(14장 단위(앱 데이터)): 셀카 얼굴 부위 PNG 주소·인증 헤더·메모리 캐시(Cache-Control max-age)·실패 처리.
 * 서버는 MockWebServer다.
 */
class FaceImageSourceTest {
    private val server = MockWebServer()
    private val paths = ConcurrentLinkedQueue<String>()
    private val auth = ConcurrentLinkedQueue<String>()
    private val clock = AtomicLong(1_000_000L)

    /** 로그인한 회원(접근 토큰 sub). null이면 로그아웃. */
    @Volatile
    private var member: Long? = 7

    /** 부위 → 응답. 기본은 30일 캐시되는 PNG 한 바이트(부위 순번). */
    private var reply: (String) -> MockResponse = { part ->
        MockResponse.Builder()
            .addHeader("Content-Type", "image/png")
            .addHeader("Cache-Control", "private, max-age=2592000")
            .body(Buffer().write(byteArrayOf(FacePart.valueOf(part).ordinal.toByte())))
            .build()
    }

    private val source: FaceImageSource by lazy {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath
                request.headers["Authorization"]?.let(auth::add)
                return reply(request.url.pathSegments.last())
            }
        }
        server.start()
        val client = ApiCoreBindings.authenticatedClient(
            OkHttpClient(),
            Tokens("access-1"),
            server.url("/api/v1/auth/refresh"),
            Json { ignoreUnknownKeys = true }
        )
        FaceImageSource(client, server.url("/api/v1/"), selfId = { member }, now = clock::get)
    }

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `selfie 키에서 접두어를 뗀 위치 + 부위로 다섯 장을 인증 헤더와 함께 받는다`() = runTest {
        val layers = (source.faceLayers("selfie:face/7/set-ab12") as Outcome.Success).value

        assertEquals(FacePart.entries, layers.map { it.part }, "그리는 순서(귀·눈·코·입·머리)")
        assertEquals(FacePart.EYES.ordinal.toByte(), layers.first { it.part == FacePart.EYES }.png.single())
        assertEquals(
            FacePart.entries.map { "/api/v1/avatar/parts/face/7/set-ab12/${it.name}" }.toSet(),
            paths.toSet()
        )
        assertTrue(auth.all { it == "Bearer access-1" } && auth.size == 5, "인증 헤더: $auth")
    }

    @Test
    fun `max-age 안에는 메모리에서 주고 지나면 다시 받는다`() = runTest {
        source.faceLayers("selfie:face/7/s1")
        source.faceLayers("selfie:face/7/s1")
        assertEquals(5, paths.size, "max-age 안에는 다시 받지 않는다")

        clock.addAndGet(2_592_000_000L + 1)
        source.faceLayers("selfie:face/7/s1")
        assertEquals(10, paths.size, "max-age가 지나면 다시 받는다")
    }

    @Test
    fun `Age만큼 지난 응답은 남은 시간만 메모리에 둔다`() = runTest {
        reply = {
            MockResponse.Builder().addHeader(
                "Cache-Control",
                "private, max-age=100"
            ).addHeader("Age", "90").body("x").build()
        }
        source.faceLayers("selfie:face/7/s1")
        clock.addAndGet(11_000)
        source.faceLayers("selfie:face/7/s1")
        assertEquals(10, paths.size, "max-age 100 - Age 90 = 10초 뒤에는 다시 받는다")
    }

    @Test
    fun `계정이 바뀌거나 로그아웃하면 앞 계정의 그림을 내주지 않는다`() = runTest {
        source.faceLayers("selfie:face/7/s1")
        member = 8
        source.faceLayers("selfie:face/7/s1")
        assertEquals(10, paths.size, "다른 계정이면 캐시를 비우고 다시 받는다")

        member = null
        assertEquals(Outcome.Failure(AppError.Unauthorized), source.faceLayers("selfie:face/7/s1"))
        assertEquals(10, paths.size, "로그아웃이면 보내지도 않는다")
    }

    @Test
    fun `no-store 응답은 메모리에 두지 않는다`() = runTest {
        reply = { MockResponse.Builder().addHeader("Cache-Control", "no-store").body("x").build() }
        source.faceLayers("selfie:face/7/s1")
        source.faceLayers("selfie:face/7/s1")
        assertEquals(10, paths.size)
    }

    @Test
    fun `부위 하나라도 받지 못하면 서버 사유 코드로 실패한다`() = runTest {
        val ok = reply
        reply = { part ->
            if (part == "NOSE") {
                MockResponse.Builder().code(409).addHeader("Content-Type", "application/json")
                    .body("""{"code":"FACE_IMAGE_NOT_FOUND","message":"없음"}""").build()
            } else {
                ok(part)
            }
        }
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("selfie:face/7/s1"))

        reply = { MockResponse.Builder().code(403).body("""{"code":"PERMISSION_DENIED"}""").build() }
        assertEquals(Outcome.Failure(AppError.Rejected("PERMISSION_DENIED")), source.faceLayers("selfie:face/8/s1"))
    }

    @Test
    fun `셀카 얼굴이 아니면 받지 않고 빈 목록, 이상한 위치는 거부한다`() = runTest {
        assertEquals(Outcome.Success(emptyList()), source.faceLayers(null))
        assertEquals(Outcome.Success(emptyList()), source.faceLayers("default:RABBIT"))
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("selfie:face/../x"))
        assertEquals(0, paths.size)
    }

    @Test
    fun `셀카 머리 얼굴은 다른 러너 것도 heads 주소로 한 번 받아 얼굴·눈·머리카락 세 층으로 겹친다`() = runTest {
        fun data(byte: Int) =
            "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(byteArrayOf(byte.toByte()))
        reply = {
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Cache-Control", "no-store")
                .body(
                    """{"headId":"h1","userId":"12","faceImage":"${data(1)}","eyesImage":"${data(2)}",""" +
                        """"hairImage":"${data(3)}","traits":{"hair":"short"},"mobile":{"canvas":[1024,1024]}}"""
                )
                .build()
        }
        val layers = (source.faceLayers("head:12:job-1") as Outcome.Success).value

        assertEquals(listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR), layers.map { it.part }, "아래부터 얼굴·눈·머리카락")
        assertEquals(listOf<Byte>(1, 2, 3), layers.map { it.png.single() })
        assertEquals(listOf("/api/v1/avatar/heads/12/job-1"), paths.toList())
        assertEquals(listOf("Bearer access-1"), auth.toList())
    }

    @Test
    fun `셀카 머리를 받지 못하거나 층이 비었거나 키가 이상하면 실패라 화면이 기본 캐릭터를 그린다`() = runTest {
        reply = { MockResponse.Builder().code(404).body("""{"reason":"HEAD_NOT_FOUND"}""").build() }
        assertTrue(source.faceLayers("head:12:job-1") is Outcome.Failure)

        reply = { MockResponse.Builder().body("""{"faceImage":"data:image/png;base64,AQ==","eyesImage":""}""").build() }
        assertTrue(source.faceLayers("head:12:job-1") is Outcome.Failure, "층 하나가 비었다")

        val sent = paths.size
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("head:x:job"))
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("head:12:a/b"))
        assertEquals(sent, paths.size, "이상한 키는 보내지 않는다")
    }

    private class Tokens(private val access: String) : TokenProvider {
        override fun accessToken(): String = access

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}
