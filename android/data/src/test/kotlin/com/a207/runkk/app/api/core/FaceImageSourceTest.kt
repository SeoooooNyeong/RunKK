package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.FaceUse
import com.a207.runkk.app.core.model.Outcome
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okio.Buffer

/**
 * FaceImageSource(14장 단위(앱 데이터)): 셀카 얼굴 부위 PNG 주소·인증 헤더·메모리 캐시(Cache-Control max-age)·실패 처리,
 * 셀카 머리 층별 받기(메타 먼저, 메타의 layerUrls 그대로, 용도별 크기 FaceUse, 더 큰 크기를 줄여 쓰기, 404 reason·503 HEAD_BUSY,
 * 같은 주소 합치기, 디스크 재사용, 계정 바뀜 경쟁, 예전 서버 대체 경로). 서버는 MockWebServer다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FaceImageSourceTest {
    private val server = MockWebServer()
    private val paths = ConcurrentLinkedQueue<String>()
    private val auth = ConcurrentLinkedQueue<String>()
    private val clock = AtomicLong(1_000_000L)

    /** 주입한 IO 디스패처. 응답 본문을 읽고 푸는 일이 여기로 넘어왔는지 센다(주 스레드에서 읽지 않는다). */
    private val io = CountingDispatcher()

    /** 로그인한 회원(접근 토큰 sub). null이면 로그아웃. */
    @Volatile
    private var member: Long? = 7

    /** 마지막 경로 조각 → 응답. 기본은 30일 캐시되는 PNG 한 바이트(부위 순번). */
    @Volatile
    private var reply: (String) -> MockResponse = { part ->
        MockResponse.Builder()
            .addHeader("Content-Type", "image/png")
            .addHeader("Cache-Control", "private, max-age=2592000")
            .body(Buffer().write(byteArrayOf(FacePart.valueOf(part).ordinal.toByte())))
            .build()
    }

    /** 있으면 메타 요청(…/meta)은 이것이 답한다(요청 경로로 층 주소를 만든다). */
    @Volatile
    private var metaReply: ((RecordedRequest) -> MockResponse)? = null

    /** 보낸 주소(경로 + 질의). 층 크기(size=)를 본다. */
    private val urls = ConcurrentLinkedQueue<String>()

    /** 있으면 서버가 이 문이 열릴 때까지 답하지 않는다(동시 요청 합치기 시험). */
    @Volatile
    private var gate: CountDownLatch? = null

    private val started by lazy {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath
                urls += request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                request.headers["Authorization"]?.let(auth::add)
                gate?.await(5, TimeUnit.SECONDS)
                val segment = request.url.pathSegments.last()
                return metaReply?.takeIf { segment == "meta" }?.invoke(request) ?: reply(segment)
            }
        }
        server.start()
        true
    }

    private val source: FaceImageSource by lazy { newSource() }

    /** 같은 서버를 쓰는 새 소스. [cache]를 주면 디스크(HTTP) 캐시를 얹는다. */
    private fun newSource(cache: Cache? = null, selfStore: SelfFaceStore = store): FaceImageSource {
        check(started)
        val client = ApiCoreBindings.authenticatedClient(
            OkHttpClient.Builder().apply { if (cache != null) cache(cache) }.build(),
            Tokens("access-1"),
            server.url("/api/v1/auth/refresh"),
            Json { ignoreUnknownKeys = true }
        )
        return FaceImageSource(client, server.url("/api/v1/"), selfId = {
            member
        }, now = clock::get, io = io, selfStore = selfStore)
    }

    /** 내 얼굴 마지막 모습 저장소(임시 폴더). */
    private val storeDir = kotlin.io.path.createTempDirectory("my-face").toFile()
    private val store = SelfFaceStore(storeDir)

    @AfterTest
    fun tearDown() {
        server.close()
        storeDir.deleteRecursively()
    }

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
    fun `목록 원은 메타를 먼저 받고 메타의 layerUrls 주소 그대로 정면 세 층을 256으로 받는다`() = runTest {
        useLayerServer()
        val layers = (source.faceLayers("head:12:job-1", FaceUse.LIST) as Outcome.Success).value

        assertEquals(listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR), layers.map { it.part }, "아래부터 얼굴·눈·머리카락")
        assertEquals(listOf<Byte>(1, 2, 3), layers.map { it.png.single() })
        assertEquals("/api/v1/avatar/heads/12/job-1/meta", urls.first(), "메타 먼저")
        assertEquals(
            listOf("face", "eyes", "hair").map { "/api/v1/avatar/heads/12/job-1/layers/$it?size=256&f=v3" }.toSet(),
            urls.drop(1).toSet()
        )
        assertEquals(4, urls.size, "메타 1 + 정면 층 3, 옆모습·통짜 머리는 받지 않는다")
        assertTrue(auth.all { it == "Bearer access-1" } && auth.size == 4, "인증 헤더: $auth")
    }

    @Test
    fun `용도마다 크기를 고른다 - 목록 256, 큰 프로필 원 512, 내 머리는 큰 아바타와 같은 1024`() = runTest {
        useLayerServer()
        source.faceLayers("head:12:job-1", FaceUse.LIST)
        source.faceLayers("head:13:job-1", FaceUse.PROFILE)
        member = 14
        source.faceLayers("head:14:job-1", FaceUse.LIST)

        val layers = urls.filter { "/layers/" in it }
        assertTrue(layers.filter { "/12/" in it }.all { "size=256" in it }, "$layers")
        assertTrue(layers.filter { "/13/" in it }.all { "size=512" in it }, "$layers")
        assertTrue(layers.filter { "/14/" in it }.all { "size=1024" in it }, "$layers")
    }

    @Test
    fun `메타에 layerUrls가 없으면(옛 서버 메타) 앱이 주소를 만든다`() = runTest {
        useLayerServer(withUrls = false)
        source.faceLayers("head:12:job-1", FaceUse.PROFILE)
        assertEquals(
            listOf("face", "eyes", "hair").map { "/api/v1/avatar/heads/12/job-1/layers/$it?size=512" }.toSet(),
            urls.drop(1).toSet()
        )
    }

    @Test
    fun `같은 러너 원 → 큰 아바타 → 원 순서에서 정면 층은 크기마다 한 번만 받고 뒤의 원은 큰 것을 줄여 쓴다`() = runTest {
        useLayerServer()
        source.faceLayers("head:12:job-1", FaceUse.LIST)
        source.selfieHead("head:12:job-1")
        val afterBig = urls.size
        val circle = (source.faceLayers("head:12:job-1", FaceUse.LIST) as Outcome.Success).value
        source.faceLayers("head:12:job-1", FaceUse.PROFILE)

        val front = urls.filter {
            it.contains("/layers/face") || it.contains("/layers/eyes") ||
                it.contains("/layers/hair")
        }
        assertEquals(3, front.count { "size=256" in it }, "$front")
        assertEquals(3, front.count { "size=1024" in it }, "$front")
        assertEquals(front.size, front.toSet().size, "같은 주소를 두 번 받지 않는다")
        assertEquals(afterBig, urls.size, "큰 아바타 뒤의 원(목록·프로필)은 받지 않는다")
        assertEquals(listOf<Byte>(1, 2, 3), circle.map { it.png.single() })
    }

    @Test
    fun `큰 프로필 원을 받은 머리는 목록 원이 다시 받지 않고, 목록 원만 있으면 큰 프로필 원은 크게 받아 덮는다`() = runTest {
        useLayerServer()
        source.faceLayers("head:12:job-1", FaceUse.PROFILE)
        val sent = urls.size
        source.faceLayers("head:12:job-1", FaceUse.LIST)
        assertEquals(sent, urls.size)

        source.faceLayers("head:13:job-1", FaceUse.LIST)
        source.faceLayers("head:13:job-1", FaceUse.PROFILE)
        source.faceLayers("head:13:job-1", FaceUse.LIST)
        val thirteen = urls.filter { "/13/" in it && "/layers/" in it }
        assertEquals(6, thirteen.size, "256 셋 뒤에 512 셋, 그 뒤 목록은 512를 줄여 쓴다: $thirteen")
    }

    @Test
    fun `내 얼굴 원과 큰 아바타가 함께 찾아도 같은 1024 층과 메타는 한 번만 받는다`() = runTest {
        member = 12
        useLayerServer()
        gate = CountDownLatch(1)
        val circle = async { source.faceLayers("head:12:job-1", FaceUse.LIST) }
        val head = async { source.selfieHead("head:12:job-1") }
        runCurrent()
        gate?.countDown()

        val front = (circle.await() as Outcome.Success).value
        val body = (head.await() as Outcome.Success).value!!
        assertEquals(7, urls.size, "메타 1 + 층 6, 정면 세 층은 함께 쓴다: $urls")
        assertEquals(urls.size, urls.toSet().size, "같은 주소를 두 번 보내지 않는다")
        assertTrue(urls.filter { "/layers/" in it }.all { "size=1024" in it }, "내 머리는 원본 크기: $urls")
        assertEquals(listOf<Byte>(1, 2, 3), front.map { it.png.single() })
        assertEquals(listOf<Byte>(1, 2, 3), body.faceLayers.map { it.png.single() })
        assertEquals(listOf<Byte>(4, 5, 6), body.rightLayers.map { it.single() })
        assertTrue(body.meta.contains("neckMask"), body.meta)
    }

    @Test
    fun `층이 503 HEAD_BUSY면 한 번 다시 받는다`() = runTest {
        useLayerServer()
        val busy = AtomicInteger()
        val layers = reply
        reply = { segment ->
            if (segment == "eyes" && busy.getAndIncrement() == 0) {
                MockResponse.Builder().code(503).addHeader("Content-Type", "application/json")
                    .body("""{"reason":"HEAD_BUSY"}""").build()
            } else {
                layers(segment)
            }
        }
        val front = (source.faceLayers("head:12:job-1", FaceUse.LIST) as Outcome.Success).value
        assertEquals(listOf<Byte>(1, 2, 3), front.map { it.png.single() })
        assertEquals(2, urls.count { "/layers/eyes" in it })
    }

    @Test
    fun `층 404의 JOB_NOT_FOUND·HEAD_NOT_FOUND는 실패이고 통짜 머리로 대체하지 않는다`() = runTest {
        useLayerServer()
        val layers = reply
        reply = { segment -> if (segment == "hair") notFound("JOB_NOT_FOUND") else layers(segment) }
        assertEquals(
            Outcome.Failure(AppError.Rejected("JOB_NOT_FOUND")),
            source.faceLayers("head:12:job-1", FaceUse.LIST)
        )
        assertTrue(paths.none { it == "/api/v1/avatar/heads/12/job-1" }, "$paths")

        metaReply = null
        reply = { notFound("HEAD_NOT_FOUND") }
        assertEquals(
            Outcome.Failure(AppError.Rejected("HEAD_NOT_FOUND")),
            source.faceLayers("head:12:job-2", FaceUse.LIST)
        )
        assertTrue(paths.none { it == "/api/v1/avatar/heads/12/job-2" }, "메타 404 사유가 있으면 옛 서버가 아니다: $paths")
    }

    @Test
    fun `층 그림은 디스크(HTTP 캐시)에 남아 새 실행에서도 서버에 다시 묻지 않고, 계정이 바뀌면 비운다`() = runTest {
        member = 12
        useLayerServer()
        val cacheDir = kotlin.io.path.createTempDirectory("face-http").toFile()
        try {
            val firstCache = Cache(cacheDir, CACHE_BYTES)
            val first = newSource(firstCache)
            first.selfieHead("head:12:job-1")
            first.faceLayers("head:7:job-9", FaceUse.LIST)
            firstCache.close()
            val sent = urls.size
            assertEquals(11, sent, "내 머리 메타·여섯 층과 다른 러너 메타·세 층")

            // 새 실행: 메모리 캐시 없이 같은 디스크 캐시와 내 얼굴 저장소
            val secondCache = Cache(cacheDir, CACHE_BYTES)
            val restarted = newSource(secondCache, SelfFaceStore(storeDir))
            val head = (restarted.selfieHead("head:12:job-1") as Outcome.Success).value!!
            val other = (restarted.faceLayers("head:7:job-9", FaceUse.LIST) as Outcome.Success).value
            assertEquals(sent, urls.size, "내 머리는 내 얼굴 저장소, 다른 러너는 디스크 캐시에서 준다")
            assertEquals(listOf<Byte>(1, 2, 3), head.faceLayers.map { it.png.single() })
            assertEquals(listOf<Byte>(4, 5, 6), head.rightLayers.map { it.single() })
            assertTrue(head.meta.contains("neckMask"), head.meta)
            assertEquals(listOf<Byte>(1, 2, 3), other.map { it.png.single() })

            member = 99
            restarted.faceLayers("head:7:job-9", FaceUse.LIST)
            assertEquals(sent + 4, urls.size, "다른 계정이면 디스크 캐시를 비우고 다시 받는다")
            secondCache.close()
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `내 머리 층은 디스크에 한 벌만 둔다(HTTP 캐시에 남기지 않고 내 얼굴 저장소에만)`() = runTest {
        member = 12
        useLayerServer()
        val cacheDir = kotlin.io.path.createTempDirectory("face-http").toFile()
        try {
            val cache = Cache(cacheDir, CACHE_BYTES)
            newSource(cache).selfieHead("head:12:job-1")
            val cached = cache.urls().asSequence().toList()
            assertTrue(cached.none { "/heads/12/" in it }, "내 머리는 HTTP 캐시에 없다: $cached")
            assertEquals(
                listOf<Byte>(1, 2, 3),
                SelfFaceStore(storeDir).layers(12, "head:12:job-1")?.map {
                    it.png.single()
                }
            )
            cache.close()
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `받는 사이 계정이 바뀌면 앞 회원의 내 머리를 기기에도 메모리에도 남기지 않는다`() = runTest {
        member = 12
        useLayerServer()
        gate = CountDownLatch(1)
        val pending = async { source.faceLayers("head:12:job-1", FaceUse.LIST) }
        runCurrent()
        member = 99
        gate?.countDown()
        pending.await()

        assertNull(SelfFaceStore(storeDir).layers(12, "head:12:job-1"), "다른 회원으로 바뀐 뒤에는 쓰지 않는다")
        member = 12
        assertNull(source.peek("head:12:job-1"))
    }

    @Test
    fun `서버가 층 엔드포인트를 모르면(메타 404, 사유 없음) 예전 통짜 머리 응답으로 받는다`() = runTest {
        reply = ::legacyServer
        val layers = (source.faceLayers("head:12:job-1", FaceUse.LIST) as Outcome.Success).value
        assertEquals(listOf<Byte>(1, 2, 3), layers.map { it.png.single() })
        assertTrue("/api/v1/avatar/heads/12/job-1" in paths, "$paths")

        val head = (source.selfieHead("head:12:job-2") as Outcome.Success).value!!
        assertEquals(listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR), head.faceLayers.map { it.part })
        assertEquals(listOf<Byte>(1, 2, 3), head.faceLayers.map { it.png.single() })
        assertEquals(listOf<Byte>(4, 5, 6), head.rightLayers.map { it.single() })
        assertTrue(head.meta.contains("\"chinY\":750"), head.meta)
        assertTrue(!head.meta.contains("data:image"), "합성 정보만 남긴다: ${head.meta}")
        assertTrue("/api/v1/avatar/heads/12/job-2" in paths)
    }

    @Test
    fun `selfieHead - 옆모습 층이 없으면(LAYER_NOT_FOUND) 그 층만 비우고, 정면을 받지 못하면 실패, 셀카 머리가 아니면 null`() = runTest {
        useLayerServer()
        reply = { segment ->
            if (segment.startsWith("right")) notFound("LAYER_NOT_FOUND") else layerServer(segment)
        }
        val head = (source.selfieHead("head:12:job-1") as Outcome.Success).value!!
        assertEquals(listOf<Byte>(1, 2, 3), head.faceLayers.map { it.png.single() })
        assertEquals(emptyList(), head.rightLayers)

        reply = { segment ->
            if (segment == "eyes") {
                MockResponse.Builder().code(503).body("""{"reason":"UPSTREAM_FAILED"}""").build()
            } else {
                layerServer(segment)
            }
        }
        assertTrue(source.selfieHead("head:12:job-2") is Outcome.Failure)

        val sent = paths.size
        assertEquals(Outcome.Success(null), source.selfieHead("selfie:face/7/s1"))
        assertEquals(Outcome.Success(null), source.selfieHead(null))
        assertEquals(sent, paths.size)
    }

    @Test
    fun `selfieHead - 메타에 옆모습 주소가 없으면(예전 머리) 옆모습을 받지 않고 비운다`() = runTest {
        useLayerServer(layers = listOf("face", "eyes", "hair"))
        val head = (source.selfieHead("head:12:job-1") as Outcome.Success).value!!
        assertEquals(emptyList(), head.rightLayers)
        assertTrue(urls.none { "/layers/right" in it }, "$urls")
    }

    @Test
    fun `셀카 머리를 받지 못하거나 층이 비었거나 키가 이상하면 실패라 화면이 기본 캐릭터를 그린다`() = runTest {
        reply = { notFound("HEAD_NOT_FOUND") }
        assertTrue(source.faceLayers("head:12:job-1") is Outcome.Failure)

        reply = { segment ->
            if (segment == "job-1") {
                MockResponse.Builder().body("""{"faceImage":"data:image/png;base64,AQ==","eyesImage":""}""").build()
            } else {
                notFound(null)
            }
        }
        assertTrue(source.faceLayers("head:12:job-1") is Outcome.Failure, "층 하나가 비었다")

        val sent = paths.size
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("head:x:job"))
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.faceLayers("head:12:a/b"))
        assertEquals(Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND")), source.selfieHead("head:12:a/b"))
        assertEquals(sent, paths.size, "이상한 키는 보내지 않는다")
    }

    @Test
    fun `응답 본문 읽기와 머리 JSON 풀기는 주입한 IO 디스패처에서 한다`() = runTest {
        source.faceLayers("selfie:face/7/s1")
        val afterParts = io.dispatches.get()
        assertTrue(afterParts >= FacePart.entries.size, "부위 다섯 장 본문을 IO에서 읽는다: $afterParts")

        useLayerServer()
        source.faceLayers("head:12:job-1")
        assertTrue(io.dispatches.get() > afterParts, "층 본문을 IO에서 읽는다")
        val afterHead = io.dispatches.get()
        source.selfieHead("head:12:job-2")
        assertTrue(io.dispatches.get() > afterHead, "selfieHead도 IO에서 읽는다")
        val afterLayers = io.dispatches.get()
        metaReply = null
        reply = ::legacyServer
        source.selfieHead("head:12:job-3")
        assertTrue(io.dispatches.get() > afterLayers, "예전 통짜 머리 JSON도 IO에서 읽고 푼다")
    }

    @Test
    fun `셀카 머리는 한 번 받으면 메모리에 두어 다시 받지 않고 바로 그리기(peek)로도 준다`() = runTest {
        useLayerServer()
        assertEquals(null, source.peek("head:12:job-1"), "받기 전에는 바로 줄 그림이 없다")
        val first = (source.faceLayers("head:12:job-1") as Outcome.Success).value
        val second = (source.faceLayers("head:12:job-1") as Outcome.Success).value

        assertEquals(4, paths.size, "메타·세 층 뒤로는 다시 받지 않는다")
        assertEquals(first, second)
        assertEquals(first, source.peek("head:12:job-1"))
        assertEquals(emptyList(), source.peek("default:RABBIT"), "기본 캐릭터는 기다릴 그림이 없다")
        assertEquals(emptyList(), source.peek(null))
    }

    @Test
    fun `셀카 부위 그림도 메모리에 다섯 장이 다 있으면 바로 준다`() = runTest {
        source.faceLayers("selfie:face/7/s1")
        val layers = source.peek("selfie:face/7/s1")
        assertEquals(FacePart.entries, layers?.map { it.part })
        assertEquals(null, source.peek("selfie:face/7/other"))
    }

    @Test
    fun `내 셀카 머리와 내 프로필 키·성별은 기기에 남아 앱을 새로 켜도 바로 그린다`() = runTest {
        member = 12
        useLayerServer()
        source.rememberSelf("head:12:job-1", "bg-1")
        source.rememberSelfGender("FEMALE")
        source.faceLayers("head:12:job-1")

        // 새 실행: 같은 폴더의 새 저장소와 새 소스(메모리 캐시 없음).
        val restarted = FaceImageSource(
            OkHttpClient(),
            server.url("/api/v1/"),
            selfId = { member },
            selfStore = SelfFaceStore(storeDir)
        )
        assertEquals(SelfFaceStore.Keys("head:12:job-1", "bg-1"), restarted.lastSelf())
        assertEquals("FEMALE", restarted.lastSelfGender())
        assertEquals(listOf<Byte>(1, 2, 3), restarted.peek("head:12:job-1")?.map { it.png.single() })
        assertEquals(4, paths.size, "새 실행은 받지 않고 남긴 것을 준다")
    }

    @Test
    fun `다른 러너 머리는 기기에 남기지 않고 다른 계정이면 남긴 내 얼굴을 내주지 않는다`() = runTest {
        member = 7
        useLayerServer()
        source.faceLayers("head:12:job-1")
        assertEquals(null, SelfFaceStore(storeDir).layers(7, "head:12:job-1"), "남의 얼굴은 남기지 않는다")

        member = 12
        source.rememberSelf("head:12:job-2", null)
        source.rememberSelfGender("MALE")
        source.faceLayers("head:12:job-2")
        member = 99
        assertEquals(null, source.lastSelf())
        assertEquals(null, source.lastSelfGender())
        assertEquals(null, source.peek("head:12:job-2"))
        source.faceLayers("selfie:face/99/s1")
        assertEquals(null, SelfFaceStore(storeDir).keys(12), "계정이 바뀌면 앞 계정의 내 얼굴을 지운다")
    }

    /**
     * 층 엔드포인트가 있는 서버. 메타는 요청 경로로 층 주소(layerUrls, 형식 판 f=v3)를 만들어 하루 캐시로 주고, 층은 이름별 PNG 한
     * 바이트(얼굴 1·눈 2·헤어 3·옆 4~6)를 immutable로 준다.
     */
    private fun useLayerServer(withUrls: Boolean = true, layers: List<String> = LAYER_BYTES.keys.toList()) {
        reply = ::layerServer
        metaReply = { request ->
            val head = request.url.encodedPath.removeSuffix("/meta")
            val urls = if (withUrls) {
                "," + """"layerUrls":{""" + listOf(256, 512, 1024).joinToString(",") { size ->
                    """"$size":{""" + layers.joinToString(",") { """"$it":"$head/layers/$it?size=$size&f=v3"""" } + "}"
                } + "}"
            } else {
                ""
            }
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Cache-Control", "private, max-age=86400")
                .addHeader("ETag", "\"meta-v3\"")
                .body(
                    """{"format":"v3","headId":"h1","canvas":1024,"layers":[""" +
                        layers.joinToString(",") { "\"$it\"" } + "]" +
                        ""","mobile":{"neckMask":{"centerX":512,"chinY":750}}$urls}"""
                )
                .build()
        }
    }

    private fun layerServer(segment: String): MockResponse = when (segment) {
        in LAYER_BYTES -> MockResponse.Builder()
            .addHeader("Content-Type", "image/png")
            .addHeader("Cache-Control", IMMUTABLE)
            .addHeader("ETag", "\"layer-$segment\"")
            .body(Buffer().write(byteArrayOf(LAYER_BYTES.getValue(segment))))
            .build()

        else -> notFound(null)
    }

    /** 층 엔드포인트를 모르는 예전 서버(v0.13.x): 새 주소는 404, 머리 주소는 통짜 JSON. */
    private fun legacyServer(segment: String): MockResponse =
        if (segment == "meta" || segment in LAYER_BYTES) notFound(null) else headReply()

    private fun notFound(reason: String?): MockResponse = MockResponse.Builder()
        .code(404)
        .addHeader("Content-Type", "application/json")
        .body(reason?.let { """{"reason":"$it"}""" } ?: """{"status":404,"error":"Not Found"}""")
        .build()

    private fun headReply(): MockResponse {
        fun data(byte: Int) =
            "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(byteArrayOf(byte.toByte()))
        return MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Cache-Control", "no-cache, private")
            .body(
                """{"headId":"h1","userId":"12","faceImage":"${data(1)}","eyesImage":"${data(2)}",""" +
                    """"hairImage":"${data(3)}","rightFaceImage":"${data(4)}","rightEyesImage":"${data(5)}",""" +
                    """"rightHairImage":"${data(6)}","mobile":{"neckMask":{"centerX":512,"chinY":750},""" +
                    """"renderFaceImage":"${data(9)}"}}"""
            )
            .build()
    }

    /** 일을 실제 IO로 넘기면서 넘겨받은 횟수를 센다. */
    private class CountingDispatcher : CoroutineDispatcher() {
        val dispatches = AtomicLong()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            Dispatchers.IO.dispatch(context, block)
        }
    }

    private class Tokens(private val access: String) : TokenProvider {
        override fun accessToken(): String = access

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        const val IMMUTABLE = "max-age=31536000, private, immutable"
        const val CACHE_BYTES = 8L * 1024 * 1024
        val LAYER_BYTES = mapOf<String, Byte>(
            "face" to 1,
            "eyes" to 2,
            "hair" to 3,
            "rightFace" to 4,
            "rightEyes" to 5,
            "rightHair" to 6
        )
    }
}
