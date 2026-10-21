package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 셀카 아바타 얼굴 부위 PNG를 받는다(GET /avatar/parts/face/{회원ID}/{묶음}/{부위}). 아바타·홈·프로필 화면이 같은 그림을 쓰므로
 * api.avatar와 api.account가 함께 쓰도록 api.core에 둔다.
 *
 * - 주소: "avatar/parts/" + (faceKey에서 "selfie:"를 뗀 값) + "/" + 부위(EYES·NOSE·MOUTH·EARS·HAIR). 인증된 OkHttp로 보내
 *   Authorization 헤더가 붙고, 401이면 TokenAuthenticator가 갱신한다.
 * - 캐시: [client]의 디스크 캐시(OkHttp Cache)가 서버 Cache-Control(private, max-age 30일)을 따른다. 그 위에 메모리 캐시를 두되
 *   응답의 max-age가 지나면 버리고, no-store·max-age가 없으면 메모리에 두지 않는다.
 * - 실패: 부위 하나라도 받지 못하면 전체를 실패로 돌려준다(반쪽 얼굴을 그리지 않고 화면이 자리 그림을 쓴다). 서버 거부는
 *   공통 오류 응답 code(403 PERMISSION_DENIED, 409 FACE_IMAGE_NOT_FOUND)로, 연결 실패는 Network로 온다.
 * - 셀카 얼굴이 아니면(기본 캐릭터·얼굴 없음) 빈 목록이다.
 */
class FaceImageSource internal constructor(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val io: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val memory = object : LinkedHashMap<String, Cached>(MEMORY_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean =
            size > MEMORY_ENTRIES
    }

    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> {
        val location = faceKey?.takeIf { it.startsWith(SELFIE_PREFIX) }?.removePrefix(SELFIE_PREFIX)
            ?: return Outcome.Success(emptyList())
        val segments = location.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
        }
        return try {
            coroutineScope {
                val layers = FacePart.entries.map { part ->
                    async { FaceLayer(part, fetch(segments, part)) }
                }.awaitAll()
                Outcome.Success(layers)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PartFailed) {
            Outcome.Failure(e.error)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    private suspend fun fetch(segments: List<String>, part: FacePart): ByteArray {
        val url = baseUrl.newBuilder().addPathSegments(PARTS_PATH).apply {
            segments.forEach { addPathSegment(it) }
        }.addPathSegment(part.name).build()
        val key = url.toString()
        synchronized(lock) {
            val cached = memory[key]
            if (cached != null && cached.expiresAt > now()) return cached.png
            if (cached != null) memory.remove(key)
        }
        return withContext(io) {
            val request = Request.Builder().url(url).header(ACCEPT, PNG).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PartFailed(ApiErrorMapper.fromHttp(response.code, response.body.string()))
                }
                val png = response.body.bytes()
                val control = response.cacheControl
                val maxAge = control.maxAgeSeconds
                if (!control.noStore && maxAge > 0) {
                    synchronized(lock) { memory[key] = Cached(png, now() + maxAge * MILLIS_PER_SECOND) }
                }
                png
            }
        }
    }

    private class Cached(val png: ByteArray, val expiresAt: Long)

    private class PartFailed(val error: AppError) : Exception()

    private companion object {
        const val SELFIE_PREFIX = "selfie:"
        const val PARTS_PATH = "avatar/parts"
        const val FACE_IMAGE_NOT_FOUND = "FACE_IMAGE_NOT_FOUND"
        const val ACCEPT = "Accept"
        const val PNG = "image/png"
        const val MEMORY_ENTRIES = 40
        const val LOAD_FACTOR = 0.75f
        const val MILLIS_PER_SECOND = 1_000L
    }
}
