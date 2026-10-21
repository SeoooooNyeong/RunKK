package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
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
 */
class FaceImageSource internal constructor(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val selfId: () -> Long?,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val memory = object : LinkedHashMap<String, Cached>(MEMORY_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean =
            size > MEMORY_ENTRIES
    }

    /** 캐시가 담고 있는 그림의 회원. */
    private var cachedFor: Long? = null

    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> {
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

    private fun layersOf(results: List<Outcome<ByteArray>>): Outcome<List<FaceLayer>> {
        val layers = FacePart.entries.zip(results).map { (part, result) ->
            when (result) {
                is Outcome.Failure -> return result
                is Outcome.Success -> FaceLayer(part, result.value)
            }
        }
        return Outcome.Success(layers)
    }

    /** 캐시를 [member]의 것으로 맞춘다. 회원이 바뀌었거나 로그아웃(null)이면 비운다. 로그인 상태면 참. */
    private fun ownCacheFor(member: Long?): Boolean {
        val changed = synchronized(lock) {
            val different = cachedFor != member
            if (different) {
                memory.clear()
                cachedFor = member
            }
            different
        }
        if (changed) {
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

    private suspend fun download(segments: List<String>, part: FacePart): Outcome<ByteArray> {
        val url = baseUrl.newBuilder().addPathSegments(PARTS_PATH).apply {
            segments.forEach { addPathSegment(it) }
        }.addPathSegment(part.name).build()
        val key = url.toString()
        synchronized(lock) {
            val cached = memory[key]
            if (cached != null && cached.expiresAt > now()) return Outcome.Success(cached.png)
            if (cached != null) memory.remove(key)
        }
        val request = Request.Builder().url(url).header(ACCEPT, PNG).build()
        return client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                return Outcome.Failure(ApiErrorMapper.fromHttp(response.code, response.body.string()))
            }
            if (response.body.contentLength() > MAX_PART_BYTES) return Outcome.Failure(AppError.Server(TOO_LARGE))
            val png = response.body.source().use { source ->
                if (source.request(MAX_PART_BYTES + 1)) return Outcome.Failure(AppError.Server(TOO_LARGE))
                source.readByteArray()
            }
            val control = response.cacheControl
            val age = response.header(AGE)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val fresh = control.maxAgeSeconds - age
            if (!control.noStore && fresh > 0) {
                synchronized(lock) { memory[key] = Cached(png, now() + fresh * MILLIS_PER_SECOND) }
            }
            Outcome.Success(png)
        }
    }

    /** 호출을 비동기로 보내고, 코루틴이 취소되면 호출도 끊는다. */
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
        const val PARTS_PATH = "avatar/parts"
        const val FACE_IMAGE_NOT_FOUND = "FACE_IMAGE_NOT_FOUND"
        const val ACCEPT = "Accept"
        const val AGE = "Age"
        const val PNG = "image/png"
        const val MEMORY_ENTRIES = 40
        const val LOAD_FACTOR = 0.75f
        const val MILLIS_PER_SECOND = 1_000L

        /** 부위 그림 한 장의 상한(2MiB). */
        const val MAX_PART_BYTES = 2L * 1024 * 1024

        /** 상한을 넘은 응답(앱이 받지 않음). */
        const val TOO_LARGE = 413
    }
}
