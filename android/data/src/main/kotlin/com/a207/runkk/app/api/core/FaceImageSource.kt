package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 셀카 아바타 얼굴 부위 PNG를 받는다(GET /avatar/parts/face/{회원ID}/{묶음}/{부위}). 아바타·홈·프로필 화면이 같은 그림을 쓰므로
 * api.avatar와 api.account가 함께 쓰도록 api.core에 둔다.
 *
 * - 주소: "avatar/parts/" + (faceKey에서 "selfie:"를 뗀 값) + "/" + 부위(EYES·NOSE·MOUTH·EARS·HAIR). 인증된 OkHttp로 보내
 *   Authorization 헤더가 붙고, 401이면 TokenAuthenticator가 갱신한다.
 * - 캐시: [client]의 디스크 캐시(OkHttp Cache)가 서버 Cache-Control(private, max-age 30일)을 따른다. 그 위에 메모리 캐시를 두되
 *   응답의 max-age(Age를 뺀 남은 시간)가 지나면 버리고, no-store·max-age가 없으면 메모리에 두지 않는다.
 * - 계정: 캐시는 받은 회원의 것이다. 로그인한 회원 ID([selfId], 접근 토큰 sub)가 바뀌거나 로그아웃하면(null) 메모리 캐시와
 *   디스크 캐시를 비운 뒤에 받는다(다른 계정에 앞 계정의 얼굴이 나가지 않게). 로그아웃 상태면 받지 않고 Unauthorized다.
 * - 실패: 부위 하나라도 받지 못하면 전체를 실패로 돌려준다(반쪽 얼굴을 그리지 않고 화면이 자리 그림을 쓴다). 서버 거부는
 *   공통 오류 응답 code(403 PERMISSION_DENIED, 409 FACE_IMAGE_NOT_FOUND)로, 연결 실패는 Network로 온다. 실패하거나 화면이 떠나
 *   취소되면 나머지 부위 요청도 끊는다. 부위 그림 하나가 [MAX_PART_BYTES]를 넘으면 받지 않는다.
 * - 셀카 얼굴이 아니면(기본 캐릭터·얼굴 없음) 빈 목록이다.
 * - 셀카 머리 얼굴("head:{회원ID}:{작업ID}", 서버 Avatar.face의 비동기 셀카 결과, S15P31A207-276)은 머리 하나를
 *   GET /avatar/heads/{회원ID}/{작업ID}(다른 러너도 받을 수 있다, 프로필 얼굴 공개 규칙)로 받아 그 안의 세 층 PNG(Data URL
 *   faceImage·eyesImage·hairImage, 같은 캔버스)를 아래부터 얼굴(EARS 자리)·눈(EYES)·머리카락(HAIR) 순서로 겹친다. 서버가 no-store로
 *   주므로 HTTP 디스크 캐시에는 두지 않는다. 층 하나라도 비었거나 PNG가 아니면 실패라 화면이 기본 캐릭터를 그린다.
 * - 셀카 머리 메모리 캐시: 머리 키는 작업마다 새로 생겨 내용이 바뀌지 않으므로, 받은 정면 세 층을 회원 캐시와 같은 규칙으로 메모리에
 *   [HEAD_ENTRIES]개 둔다(화면마다 다시 받지 않는다). 내 머리(키의 회원 ID = 로그인한 회원)는 [selfStore]에도 남겨 앱을 새로 켜도 바로
 *   그린다.
 * - 바로 그리기([peek]): 받아 둔 그림이 있으면 기다리지 않고 준다. 화면이 첫 그림부터 내 얼굴을 그리고 기본 머리를 거치지 않게 한다.
 */
class FaceImageSource internal constructor(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val selfId: () -> Long?,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** 내 얼굴의 마지막 모습(앱 전용 저장소). 없으면 남기지 않는다. */
    private val selfStore: SelfFaceStore? = null
) {
    private val lock = Any()
    private val memory = object : LinkedHashMap<String, Cached>(MEMORY_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean =
            size > MEMORY_ENTRIES
    }

    /** 받은 셀카 머리 정면 세 층(머리 키 "{회원ID}:{작업ID}" → 층). */
    private val heads = object : LinkedHashMap<String, List<FaceLayer>>(HEAD_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<FaceLayer>>?): Boolean =
            size > HEAD_ENTRIES
    }

    /** 캐시가 담고 있는 그림의 회원. */
    private var cachedFor: Long? = null

    /**
     * 기다리지 않고 바로 그릴 수 있는 얼굴 그림. 셀카 얼굴이 아니면(기본 캐릭터·없음) 빈 목록, 받아 둔 그림(메모리, 내 얼굴이면 기기에
     * 남긴 것)이 있으면 그 층, 없으면 null(그때는 [faceLayers]로 받는다). 로그인한 회원의 캐시만 본다. 네트워크는 쓰지 않는다.
     */
    fun peek(faceKey: String?): List<FaceLayer>? {
        val member = selfId() ?: return null
        if (faceKey == null) return emptyList()
        if (faceKey.startsWith(HEAD_PREFIX)) {
            val location = faceKey.removePrefix(HEAD_PREFIX)
            synchronized(lock) { if (cachedFor == member) heads[location] else null }?.let { return it }
            val saved = selfStore?.layers(member, faceKey) ?: return null
            synchronized(lock) { if (cachedFor == member || cachedFor == null) heads[location] = saved }
            return saved
        }
        val location = faceKey.takeIf { it.startsWith(SELFIE_PREFIX) }?.removePrefix(SELFIE_PREFIX)
            ?: return emptyList()
        val segments = location.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return synchronized(lock) {
            if (cachedFor != member) return null
            FacePart.entries.map { part ->
                val cached = memory[partUrl(segments, part).toString()]
                if (cached == null || cached.expiresAt <= now()) return null
                FaceLayer(part, cached.png)
            }
        }
    }

    /** 내 프로필의 얼굴·배경 키를 남긴다(본인 프로필을 읽을 때마다). 로그아웃 상태면 남기지 않는다. */
    suspend fun rememberSelf(faceKey: String?, backgroundKey: String?) {
        val member = selfId() ?: return
        val store = selfStore ?: return
        withContext(io) { store.saveKeys(member, SelfFaceStore.Keys(faceKey, backgroundKey)) }
    }

    /** 내 프로필의 마지막 얼굴·배경 키(기기에 남긴 것). 모르거나 다른 회원 것이면 null. */
    internal fun lastSelf(): SelfFaceStore.Keys? = selfStore?.keys(selfId())

    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> {
        if (faceKey != null && faceKey.startsWith(HEAD_PREFIX)) return headLayers(faceKey.removePrefix(HEAD_PREFIX))
        val location = faceKey?.takeIf { it.startsWith(SELFIE_PREFIX) }?.removePrefix(SELFIE_PREFIX)
            ?: return Outcome.Success(emptyList())
        val segments = location.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
        }
        if (!ownCacheFor(selfId())) return Outcome.Failure(AppError.Unauthorized)
        return loadLayers(segments)
    }

    /**
     * 셀카 머리 얼굴("{회원ID}:{작업ID}") 하나를 받아 세 층으로 바꾼다. 층 하나라도 틀리면 실패다. 받은 층은 메모리에 두고(머리 키는 내용이
     * 바뀌지 않는다), 내 머리면 기기에도 남긴다.
     */
    private suspend fun headLayers(location: String): Outcome<List<FaceLayer>> {
        val member = selfId()
        synchronized(lock) { if (member != null && cachedFor == member) heads[location] else null }
            ?.let { return Outcome.Success(it) }
        val result = headBody(location) { body ->
            headOf(body)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Server(MALFORMED))
        }
        val layers = (result as? Outcome.Success)?.value ?: return result
        synchronized(lock) { if (cachedFor == member) heads[location] = layers }
        val owner = location.substringBefore(':').toLongOrNull()
        val store = selfStore
        if (member != null && owner == member && store != null) {
            withContext(io) { store.saveLayers(member, HEAD_PREFIX + location, layers) }
        }
        return result
    }

    /**
     * 셀카 머리 결과(W9 Rive 렌더러 입력, contract-W8.md 6.2: 정면 3층 + 옆모습 3층 + 원본 2장 + mobile)를 한 번 받아 원문 JSON과
     * 대체 그림(정면 세 층)을 함께 준다. 층 형식이 틀리면 대체 그림만 빈 목록이다(받기는 성공). 셀카 머리 얼굴("head:…")이 아니면
     * null이다. 서버가 no-store로 주므로 메모리에 두지 않는다.
     */
    internal suspend fun selfieHead(faceKey: String?): Outcome<HeadBody?> {
        if (faceKey == null || !faceKey.startsWith(HEAD_PREFIX)) return Outcome.Success(null)
        return headBody(faceKey.removePrefix(HEAD_PREFIX)) { body ->
            Outcome.Success(HeadBody(body, headOf(body).orEmpty()))
        }
    }

    /**
     * GET /avatar/heads/{회원ID}/{작업ID} 본문을 받아 [parse]로 바꾼다. 키 형식이 틀리면 FACE_IMAGE_NOT_FOUND, [MAX_HEAD_BYTES]를
     * 넘으면 실패다. 본문 읽기(최대 32MiB)와 [parse](Base64·JSON)는 [io]에서 한다(부른 쪽이 주 스레드여도 막지 않는다).
     */
    private suspend fun <T> headBody(location: String, parse: (String) -> Outcome<T>): Outcome<T> {
        val segments = location.split(':')
        if (segments.size != 2 || segments[0].toLongOrNull() == null ||
            segments.any { it.isBlank() || it.contains('/') }
        ) {
            return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
        }
        if (!ownCacheFor(selfId())) return Outcome.Failure(AppError.Unauthorized)
        val url = baseUrl.newBuilder().addPathSegments(HEADS_PATH).addPathSegment(segments[0])
            .addPathSegment(segments[1]).build()
        val request = Request.Builder().url(url).header(ACCEPT, JSON).build()
        return try {
            client.newCall(request).await().use { response ->
                withContext(io) {
                    when {
                        !response.isSuccessful ->
                            Outcome.Failure(ApiErrorMapper.fromHttp(response.code, response.body.string()))

                        response.body.source().request(
                            MAX_HEAD_BYTES + 1
                        ) -> Outcome.Failure(AppError.Server(TOO_LARGE))

                        else -> parse(response.body.source().readUtf8())
                    }
                }
            }
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    /** 머리 응답 JSON의 세 Data URL을 층으로 바꾼다. JSON이 아니거나 층 하나라도 틀리면 null. */
    private fun headOf(body: String): List<FaceLayer>? {
        val head = try {
            HEAD_JSON.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        val layers = HEAD_LAYERS.map { (field, part) ->
            val png = (head[field] as? JsonPrimitive)?.contentOrNull?.let(::pngOf) ?: return null
            FaceLayer(part, png)
        }
        return layers
    }

    /** "data:image/png;base64,…"의 PNG 바이트. 형식이 다르거나 비었으면 null. */
    private fun pngOf(dataUrl: String): ByteArray? {
        if (!dataUrl.startsWith(DATA_PNG)) return null
        val bytes = try {
            Base64.getDecoder().decode(dataUrl.substring(DATA_PNG.length))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_PART_BYTES }
    }

    /**
     * 다섯 부위를 함께 받는다. 실패는 값(Outcome)으로 받고(리뷰 N-8: 예외로 흐름을 넘기지 않는다), 먼저 온 실패가 나머지 요청을 끊는다.
     */
    private suspend fun loadLayers(segments: List<String>): Outcome<List<FaceLayer>> = coroutineScope {
        val firstFailure = CompletableDeferred<Outcome.Failure>()
        val parts = FacePart.entries.map { part ->
            async { fetch(segments, part).also { if (it is Outcome.Failure) firstFailure.complete(it) } }
        }
        val all = async { parts.awaitAll() }
        val outcome =
            select {
                firstFailure.onAwait { it }
                all.onAwait { results -> layersOf(results) }
            }
        coroutineContext.cancelChildren()
        outcome
    }

    /** 실패가 하나라도 있으면 첫 실패를 돌려주고, 아니면 부위 순서대로 층을 만든다(반복 안에서 빠져나가지 않는다, N3-3). */
    private fun layersOf(results: List<Outcome<ByteArray>>): Outcome<List<FaceLayer>> {
        val failure = results.filterIsInstance<Outcome.Failure>().firstOrNull()
        if (failure != null) return failure
        val images = results.mapNotNull { if (it is Outcome.Success) it.value else null }
        return Outcome.Success(FacePart.entries.zip(images) { part, image -> FaceLayer(part, image) })
    }

    /** 캐시를 [member]의 것으로 맞춘다. 회원이 바뀌었거나 로그아웃(null)이면 비운다. 로그인 상태면 참. */
    private fun ownCacheFor(member: Long?): Boolean {
        val changed = synchronized(lock) {
            val different = cachedFor != member
            if (different) {
                memory.clear()
                heads.clear()
                cachedFor = member
            }
            different
        }
        if (changed) {
            // 앞 회원이 남긴 내 얼굴도 지운다(다른 계정에 앞 계정 얼굴이 첫 그림으로 나가지 않게).
            selfStore?.clearUnless(member)
            try {
                client.cache?.evictAll()
            } catch (e: IOException) {
                // 디스크 캐시를 비우지 못하면 다음 요청이 서버에 다시 확인하도록 둔다(메모리는 비웠다).
            }
        }
        return member != null
    }

    /** 부위 하나. 연결 실패는 Network, 서버 거부·상한 초과는 그 오류로 돌려준다. */
    private suspend fun fetch(segments: List<String>, part: FacePart): Outcome<ByteArray> = try {
        download(segments, part)
    } catch (e: IOException) {
        Outcome.Failure(AppError.Network)
    }

    private fun partUrl(segments: List<String>, part: FacePart): HttpUrl = baseUrl.newBuilder()
        .addPathSegments(PARTS_PATH)
        .apply { segments.forEach { addPathSegment(it) } }
        .addPathSegment(part.name)
        .build()

    private suspend fun download(segments: List<String>, part: FacePart): Outcome<ByteArray> {
        val url = partUrl(segments, part)
        val key = url.toString()
        synchronized(lock) {
            val cached = memory[key]
            if (cached != null && cached.expiresAt > now()) return Outcome.Success(cached.png)
            if (cached != null) memory.remove(key)
        }
        val request = Request.Builder().url(url).header(ACCEPT, PNG).build()
        return client.newCall(request).await().use { response ->
            val read = withContext(io) { pngBody(response) }
            val png = (read as? Outcome.Success)?.value ?: return read
            val control = response.cacheControl
            val age = response.header(AGE)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val fresh = control.maxAgeSeconds - age
            if (!control.noStore && fresh > 0) {
                synchronized(lock) { memory[key] = Cached(png, now() + fresh * MILLIS_PER_SECOND) }
            }
            Outcome.Success(png)
        }
    }

    /** 부위 응답 본문(상한 [MAX_PART_BYTES]). [io]에서 부른다. */
    private fun pngBody(response: Response): Outcome<ByteArray> {
        if (!response.isSuccessful) {
            return Outcome.Failure(
                ApiErrorMapper.fromHttp(response.code, response.body.string())
            )
        }
        if (response.body.contentLength() > MAX_PART_BYTES) return Outcome.Failure(AppError.Server(TOO_LARGE))
        val source = response.body.source()
        if (source.request(MAX_PART_BYTES + 1)) return Outcome.Failure(AppError.Server(TOO_LARGE))
        return Outcome.Success(source.readByteArray())
    }

    /** 호출을 비동기로 보내고, 코루틴이 취소되면 호출도 끊는다. 결과는 부른 쪽 디스패처로 돌아오므로 본문은 [io]에서 읽는다. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        )
    }

    private class Cached(val png: ByteArray, val expiresAt: Long)

    private companion object {
        const val SELFIE_PREFIX = "selfie:"
        const val HEAD_PREFIX = "head:"
        const val HEADS_PATH = "avatar/heads"
        const val JSON = "application/json"
        const val DATA_PNG = "data:image/png;base64,"

        /** 머리 응답 한 개의 상한(정면·옆모습 층과 원본 PNG의 base64, 합성 정보, 32MiB. 서버 중계 상한과 같다). */
        const val MAX_HEAD_BYTES = 32L * 1024 * 1024

        /** 머리 응답을 읽지 못함(앱이 그리지 않음). */
        const val MALFORMED = 502

        /** 머리 응답 항목 → 그리는 순서 자리(얼굴 바탕·눈·머리카락). */
        val HEAD_LAYERS = listOf(
            "faceImage" to FacePart.EARS,
            "eyesImage" to FacePart.EYES,
            "hairImage" to FacePart.HAIR
        )
        val HEAD_JSON = Json { ignoreUnknownKeys = true }
        const val PARTS_PATH = "avatar/parts"
        const val FACE_IMAGE_NOT_FOUND = "FACE_IMAGE_NOT_FOUND"
        const val ACCEPT = "Accept"
        const val AGE = "Age"
        const val PNG = "image/png"
        const val MEMORY_ENTRIES = 40

        /** 메모리에 두는 셀카 머리 수(머리 하나는 정면 세 층 PNG, 수백 KB). */
        const val HEAD_ENTRIES = 12
        const val LOAD_FACTOR = 0.75f
        const val MILLIS_PER_SECOND = 1_000L

        /** 부위 그림 한 장의 상한(2MiB). */
        const val MAX_PART_BYTES = 2L * 1024 * 1024

        /** 상한을 넘은 응답(앱이 받지 않음). */
        const val TOO_LARGE = 413
    }
}
