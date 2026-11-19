package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.SelfFaceStore
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.domain.sync.PreloadAsset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import retrofit2.create

/**
 * 시작 에셋 미리 받기 출처(S15P31A207-345): 공용 에셋은 모두 앱에 들어 있어 받을 것이 없고, 회원 에셋은 내 셀카 머리(메타 + 1024 층
 * 여섯 장)다. 얼굴 키는 기기에 남긴 것을 쓰고(네트워크 없음) 모를 때만 GET /avatar로 묻는다. 받은 것은 화면이 쓰는 내 얼굴 저장소에
 * 남아 앱을 새로 켜도 적중한다.
 */
class AvatarAssetPreloadSourceTest {
    private val api = ApiTestServer()
    private val paths = ConcurrentLinkedQueue<String>()
    private val storeDir = kotlin.io.path.createTempDirectory("my-face").toFile()

    @Volatile
    private var avatarFaceKey: String? = HEAD

    init {
        api.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath
                val segment = request.url.pathSegments.last()
                return when {
                    request.url.encodedPath == "/api/v1/avatar" -> json(avatarJson())

                    segment == "meta" -> json(meta(request.url.encodedPath.removeSuffix("/meta")))

                    segment in LAYERS -> MockResponse.Builder()
                        .addHeader("Content-Type", "image/png")
                        .body(Buffer().write(byteArrayOf(LAYERS.indexOf(segment).toByte())))
                        .build()

                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
    }

    private fun faces() = FaceImageSource(
        OkHttpClient(),
        api.server.url("/api/v1/"),
        selfId = { 7L },
        selfStore = SelfFaceStore(storeDir)
    )

    private fun source(faces: FaceImageSource = faces(), marks: MutableList<String> = mutableListOf()) =
        AvatarAssetPreloadSource(api.retrofit.create(), faces) { marks += it }

    @AfterTest
    fun tearDown() {
        api.close()
        storeDir.deleteRecursively()
    }

    @Test
    fun `공용 에셋은 모두 앱에 들어 있어 받을 것이 없다`() = runTest {
        assertEquals(emptyList(), source().publicAssets().value())
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `내 얼굴 키를 모르면 GET avatar로 묻고 셀카 머리면 메타와 층 여섯 장을 받을 목록에 넣는다`() = runTest {
        assertEquals(listOf(PreloadAsset(HEAD, files = 7)), source().memberAssets().value())
        assertEquals(listOf("/api/v1/avatar"), paths.toList())
    }

    @Test
    fun `기기에 남긴 얼굴 키가 있으면 묻지 않고, 셀카 머리가 아니면(기본 캐릭터) 받을 것이 없다`() = runTest {
        val faces = faces()
        faces.rememberSelf(HEAD, null)
        assertEquals(listOf(PreloadAsset(HEAD, files = 7)), source(faces).memberAssets().value())
        faces.rememberSelf(null, null)
        assertEquals(emptyList(), source(faces).memberAssets().value())
        assertTrue(paths.isEmpty(), "네트워크를 쓰지 않는다: $paths")
    }

    @Test
    fun `받은 머리는 내 얼굴 저장소에 남아 앱을 새로 켜도 적중하고 다시 받지 않는다`() = runTest {
        val first = source()
        val asset = PreloadAsset(HEAD, files = 7)
        assertFalse(first.isCached(asset))
        val files = AtomicInteger()

        first.fetch(asset) { files.incrementAndGet() }.value()

        assertEquals(7, files.get())
        assertEquals(7, paths.size, "메타 + 층 여섯")
        val restarted = faces()
        assertTrue(source(restarted).isCached(asset), "새 실행도 적중")
        // 화면(큰 아바타)이 같은 저장소에서 받아 네트워크를 쓰지 않는다.
        val head = restarted.selfieHead(HEAD).value()
        assertEquals(6, head!!.faceLayers.size + head.rightLayers.size)
        assertEquals(7, paths.size, "화면은 다시 받지 않는다")
    }

    @Test
    fun `표식은 한 줄씩 남긴다`() {
        val marks = mutableListOf<String>()
        source(marks = marks).mark("AssetPreload hit $HEAD")
        assertEquals(listOf("AssetPreload hit $HEAD"), marks)
    }

    private fun avatarJson() =
        """{"faceKey":${avatarFaceKey?.let { "\"$it\"" } ?: "null"},"bodyShape":"STAGE1","skinTone":"DAYTIME"}"""

    private fun meta(head: String) = """{"format":"v3","headId":"h1","canvas":1024,"layerUrls":{"1024":{""" +
        LAYERS.joinToString(",") { """"$it":"$head/layers/$it?size=1024&f=v3"""" } + "}}}"

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private companion object {
        const val HEAD = "head:7:job-1"
        val LAYERS = listOf("face", "eyes", "hair", "rightFace", "rightEyes", "rightHair")
    }
}
