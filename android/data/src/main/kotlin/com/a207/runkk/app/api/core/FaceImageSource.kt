package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.FaceUse
import com.a207.runkk.app.core.model.Outcome
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 셀카 아바타 얼굴 그림을 받는다. 아바타·홈·프로필·랭킹·크루·채팅 화면이 같은 그림을 쓰므로 api.avatar와 api.account 등이 함께 쓰도록
 * api.core에 둔다.
 *
 * - 셀카 부위 얼굴("selfie:…"): GET /avatar/parts/{faceKey에서 "selfie:"를 뗀 값}/{부위}(EYES·NOSE·MOUTH·EARS·HAIR). 인증된
 *   OkHttp로 보내 Authorization 헤더가 붙고, 401이면 TokenAuthenticator가 갱신한다. 메모리 캐시는 응답의 max-age(Age를 뺀 남은
 *   시간)가 지나면 버리고, no-store·max-age가 없으면 메모리에 두지 않는다.
 * - 셀카 머리 얼굴("head:{회원ID}:{작업ID}", 레이어 합성): 메타(…/meta, 하루 캐시)를 먼저 받고 메타의 층 주소(layerUrls: 크기 →
 *   층 이름 → 주소, 서버 형식 판 포함)를 그대로 쓴다. 메타에 주소가 없으면(옛 서버 메타) 앱이 …/layers/{층}?size=로 만든다.
 *   - 얼굴 원([faceLayers]): 정면 세 층을 그릴 자리([FaceUse]) 크기로 받아 아래부터 얼굴(EARS 자리)·눈(EYES)·머리카락(HAIR) 순서로
 *     겹친다. 목록 원 256, 큰 프로필 원 512. 내 머리는 큰 아바타와 같은 1024를 써서 같은 그림을 한 번만 받는다(Jira 308·309).
 *   - 큰 아바타([selfieHead]): 메타와 1024 층 여섯 장(정면 셋·옆모습 셋). 메타에 옆모습 주소가 없거나 층이 없으면(404
 *     LAYER_NOT_FOUND) 옆모습만 비운다.
 *   - 메모리 표의 열쇠는 머리(소유자, 작업)이고 값은 가진 가장 큰 크기의 정면 층이다. 더 큰 크기가 있으면 작은 크기를 받지 않고 그것을
 *     쓰고(화면이 줄여 그린다), 작은 크기만 있는데 큰 크기가 필요하면 큰 크기를 받아 덮는다. 큰 아바타는 메모리의 1024 정면 층을 다시
 *     받지 않는다.
 *   - 같은 주소를 여러 화면이 함께 찾으면 한 번만 보낸다(받는 중 표 [inflight]). 다른 러너의 층·메타는 [client]의 디스크 캐시(OkHttp
 *     Cache, 서버 Cache-Control)가 앱을 다시 켜도 준다. 내 머리(키의 회원 ID = 로그인한 회원)는 HTTP 캐시에 남기지 않고(no-store)
 *     [selfStore]에 한 벌만 남긴다: 정면 세 층, 큰 아바타를 받았으면 옆모습 층과 메타까지. 앱을 새로 켜면 받지 않고 그것을 준다.
 *   - 층 응답: 503 HEAD_BUSY면 한 번 다시 받는다. 404의 reason이 LAYER_NOT_FOUND면 그 층이 없는 것, JOB_NOT_FOUND·HEAD_NOT_FOUND
 *     등이면 실패다. reason 없는 404·405만 옛 서버(v0.13.x, 층 주소 없음)로 보고 통짜 응답 GET /avatar/heads/{회원ID}/{작업ID}
 *     (Base64 PNG JSON)에서 같은 층을 꺼낸다.
 * - 계정(S15P31A207-345 2차, 받아 둔 것은 지우지 않는다): 로그인한 회원 ID([selfId], 접근 토큰 sub)가 바뀌거나 로그아웃하면(null)
 *   메모리 캐시만 비운다. 디스크 캐시의 다른 러너 머리는 공용이라 계정과 상관없이 같이 쓰고, 내 얼굴은 [selfStore]가 계정별 칸에 두어
 *   다른 계정에 섞이지 않고 앞 계정으로 돌아오면 바로 준다. 받는 사이 회원이 바뀌었으면 받은 내 머리를 메모리·기기에 두지 않는다.
 *   로그아웃 상태면 받지 않고 Unauthorized다.
 * - 실패: 부위·정면 층 하나라도 받지 못하면 전체를 실패로 돌려준다(반쪽 얼굴을 그리지 않고 화면이 자리 그림을 쓴다). 서버 거부는 공통
 *   오류 응답 code·reason(403 PERMISSION_DENIED, 409 FACE_IMAGE_NOT_FOUND, 404 JOB_NOT_FOUND 등)으로, 연결 실패는 Network로 온다.
 * - 셀카 얼굴이 아니면(기본 캐릭터·얼굴 없음) 빈 목록이다.
 * - 바로 그리기([peek]): 받아 둔 그림이 있으면 기다리지 않고 준다. 화면이 첫 그림부터 내 얼굴을 그리고 기본 머리를 거치지 않게 한다.
 * - 이어 받기(S15P31A207-345, 시작 로딩 화면): 내 머리를 받다 층 하나라도 실패하면, 그 사이 얻은 메타·층은 [mine](메모리)에 두어
 *   다시 받을 때 서버에 묻지 않고, 얻은 정면 세 층은 기기에도 남긴다. 머리 전체를 남기면 [mine]을 비운다.
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

    /** 받은 셀카 머리 정면 세 층(머리 키 "{회원ID}:{작업ID}" → 가진 가장 큰 크기의 층). */
    private val heads = object : LinkedHashMap<String, HeldHead>(HEAD_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HeldHead>?): Boolean =
            size > HEAD_ENTRIES
    }

    /** 받는 중인 머리 층·메타 요청(주소 → 결과). 같은 주소를 함께 찾은 쪽은 새로 보내지 않고 이것을 기다린다. */
    private val inflight = HashMap<String, Deferred<Fetched>>()

    /** 층·메타 받기. 찾은 화면이 떠나도 받기는 끝까지 해 디스크 캐시에 남긴다(같은 머리를 다른 화면이 곧 다시 찾는다). */
    private val fetches = CoroutineScope(SupervisorJob() + io)

    /** 캐시가 담고 있는 그림의 회원. */
    private var cachedFor: Long? = null

    /** 내 머리를 받다 멈춘 사이 얻은 메타·층(주소 → 본문). 다시 받을 때 이어 받는다. 머리 전체를 남기거나 회원이 바뀌면 비운다. */
    private val mine = HashMap<String, ByteArray>()

    /**
     * 기다리지 않고 바로 그릴 수 있는 얼굴 그림. 셀카 얼굴이 아니면(기본 캐릭터·없음) 빈 목록, 받아 둔 그림(메모리, 내 얼굴이면 기기에
     * 남긴 것)이 있으면 그 층, 없으면 null(그때는 [faceLayers]로 받는다). 로그인한 회원의 캐시만 본다. 네트워크는 쓰지 않는다.
     */
    fun peek(faceKey: String?): List<FaceLayer>? {
        val member = selfId() ?: return null
        if (faceKey == null) return emptyList()
        if (faceKey.startsWith(HEAD_PREFIX)) {
            val location = faceKey.removePrefix(HEAD_PREFIX)
            synchronized(lock) { if (cachedFor == member) heads[location]?.layers else null }?.let { return it }
            val saved = selfStore?.layers(member, faceKey) ?: return null
            synchronized(lock) {
                if (cachedFor == member || cachedFor == null) hold(location, HeldHead(FaceUse.FULL.size(), saved))
            }
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

    /** 내 성별(이름)을 남긴다. 앱을 새로 켜면 프로필 응답 전에 이 성별로 큰 아바타 합성을 시작한다(Jira 307). */
    suspend fun rememberSelfGender(gender: String) {
        val member = selfId() ?: return
        val store = selfStore ?: return
        withContext(io) { store.saveGender(member, gender) }
    }

    /** 내 프로필의 마지막 얼굴·배경 키(기기에 남긴 것). 모르거나 다른 회원 것이면 null. */
    internal fun lastSelf(): SelfFaceStore.Keys? = selfStore?.keys(selfId())

    /** 내 마지막 성별(기기에 남긴 것). 모르거나 다른 회원 것이면 null. */
    internal fun lastSelfGender(): String? = selfStore?.gender(selfId())

    /** [faceKey] 얼굴을 [use] 자리에 그릴 층. 셀카 부위 얼굴은 크기가 하나라 [use]를 보지 않는다. */
    suspend fun faceLayers(faceKey: String?, use: FaceUse = FaceUse.LIST): Outcome<List<FaceLayer>> {
        if (faceKey != null &&
            faceKey.startsWith(HEAD_PREFIX)
        ) {
            return headLayers(faceKey.removePrefix(HEAD_PREFIX), use)
        }
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
     * 얼굴 원의 셀카 머리("{회원ID}:{작업ID}"): 메타를 받은 뒤 정면 세 층을 [use] 크기로 받는다(내 머리는 1024). 같은 크기 이상을 이미
     * 가지고 있으면 받지 않는다. 층 하나라도 틀리면 실패다.
     */
    private suspend fun headLayers(location: String, use: FaceUse): Outcome<List<FaceLayer>> {
        val head = HeadKey.of(location) ?: return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
        val member = selfId()
        val size = sizeFor(head, member, use)
        held(member, location, size)?.let { return Outcome.Success(it) }
        if (!ownCacheFor(member)) return Outcome.Failure(AppError.Unauthorized)
        val isMine = head.owner == member
        if (isMine && member != null) {
            selfStore?.layers(member, HEAD_PREFIX + location)?.let { saved ->
                remember(member, head, location, FaceUse.FULL.size(), saved)
                return Outcome.Success(saved)
            }
        }
        val meta = when (val fetched = fetchHead(metaUrl(head), isMine)) {
            is Fetched.Body -> HeadMeta.of(fetched.bytes)
            is Fetched.Failed -> return fetched.failure
            Fetched.Missing, Fetched.Unsupported -> return legacyFront(member, head, location)
        }
        val fetched = coroutineScope {
            FRONT_LAYERS.map { (name, _) -> async { fetchLayer(head, meta, name, size, isMine) } }.awaitAll()
        }
        if (fetched.any { it is Fetched.Unsupported }) return legacyFront(member, head, location)
        val result = frontOf(fetched)
        val layers = (result as? Outcome.Success)?.value ?: return result
        remember(member, head, location, size, layers)
        return result
    }

    /**
     * 내 셀카 머리 전체(정면·옆모습·메타)를 지금 형식 판으로 기기에 다 남겼는가(시작 에셋 미리 받기의 캐시 적중, S15P31A207-345).
     * 네트워크를 쓰지 않고 그림도 읽지 않는다. 로그아웃이거나 남의 머리면 거짓.
     */
    internal fun hasSelfHead(faceKey: String): Boolean {
        val member = selfId() ?: return false
        return selfStore?.hasHead(member, faceKey) == true
    }

    /**
     * 큰 아바타의 셀카 머리(W9 Rive 렌더러 입력): 메타와 1024 층 여섯 장(정면 셋·옆모습 셋)으로 정면 세 층·옆모습 세 층·메타 JSON을
     * 준다. 메모리에 1024 정면 층이 있으면 그것을 쓰고, 내 머리를 기기에 남겼으면 받지 않는다. 옆모습 층이 없으면 옆모습만 빈 목록이다.
     * 서버가 층 주소를 모르면 통짜 응답에서 꺼낸다(층 형식이 틀리면 대체 그림만 빈 목록, 받기는 성공). 셀카 머리 얼굴("head:…")이
     * 아니면 null이다. [onFile]은 메타·층 파일 하나를 얻을 때마다 부른다(시작 로딩 화면의 진행 막대, 여러 스레드에서 온다).
     */
    internal suspend fun selfieHead(faceKey: String?, onFile: () -> Unit = {}): Outcome<HeadBody?> {
        if (faceKey == null || !faceKey.startsWith(HEAD_PREFIX)) return Outcome.Success(null)
        val location = faceKey.removePrefix(HEAD_PREFIX)
        val head = HeadKey.of(location) ?: return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
        val member = selfId()
        if (!ownCacheFor(member)) return Outcome.Failure(AppError.Unauthorized)
        val isMine = head.owner == member
        if (isMine && member != null) {
            selfStore?.head(member, faceKey)?.let { saved ->
                remember(member, head, location, FaceUse.FULL.size(), saved.faceLayers)
                return Outcome.Success(HeadBody(saved.faceLayers, saved.rightLayers, saved.meta))
            }
        }
        val metaFetched = fetchHead(metaUrl(head), isMine).also { if (it is Fetched.Body) onFile() }
        val metaBytes = when (metaFetched) {
            is Fetched.Body -> metaFetched.bytes

            is Fetched.Failed -> return metaFetched.failure

            Fetched.Missing, Fetched.Unsupported -> return headBody(head) { body ->
                Outcome.Success(legacyHeadOf(body))
            }
        }
        val meta = HeadMeta.of(metaBytes)
        val full = FaceUse.FULL.size()
        val known = held(member, location, full) ?: member?.takeIf { isMine }?.let { selfStore?.layers(it, faceKey) }
        val (front, side) = coroutineScope {
            val counted = { fetched: Fetched -> fetched.also { if (it !is Fetched.Failed) onFile() } }
            val front = if (known == null) {
                FRONT_LAYERS.map { (name, _) -> async { counted(fetchLayer(head, meta, name, full, isMine)) } }
            } else {
                null
            }
            val side = SIDE_LAYERS.map { name -> async { counted(fetchLayer(head, meta, name, full, isMine)) } }
            front?.awaitAll() to side.awaitAll()
        }
        val faceLayers = known ?: when (val result = frontOf(front.orEmpty())) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        side.filterIsInstance<Fetched.Failed>().firstOrNull()?.let { failed ->
            // 받은 정면 세 층은 남긴다(다시 받을 때 정면은 받지 않는다, S15P31A207-345).
            if (known == null) remember(member, head, location, full, faceLayers)
            return failed.failure
        }
        val rightLayers = side.map { (it as? Fetched.Body)?.bytes }.takeIf { all -> all.none { it == null } }
            ?.filterNotNull().orEmpty()
        val body = HeadBody(faceLayers, rightLayers, metaBytes.decodeToString())
        remember(member, head, location, full, faceLayers, body)
        return Outcome.Success(body)
    }

    /** 이 머리를 받을 크기. 내 머리는 큰 아바타와 같은 1024다(같은 그림을 크기별로 두 번 받지 않게). */
    private fun sizeFor(head: HeadKey, member: Long?, use: FaceUse): Int =
        if (head.owner == member) FaceUse.FULL.size() else use.size()

    /** 메모리에 [size] 이상으로 가진 정면 층. 로그인한 회원의 캐시만 본다. */
    private fun held(member: Long?, location: String, size: Int): List<FaceLayer>? = synchronized(lock) {
        if (member == null || cachedFor != member) return null
        heads[location]?.takeIf { it.size >= size }?.layers
    }

    /** 가진 것보다 크거나 같은 크기만 둔다(작은 것이 큰 것을 덮지 않는다). [lock] 안에서 부른다. */
    private fun hold(location: String, next: HeldHead) {
        val current = heads[location]
        if (current == null || current.size <= next.size) heads[location] = next
    }

    /**
     * 받은 정면 세 층을 메모리에 두고, 내 머리면 기기에도 남긴다([full]이 있으면 옆모습·메타까지). 받는 사이 로그인한 회원이 바뀌었으면
     * 두지 않는다(앞 회원의 얼굴이 다음 회원 캐시·기기에 남지 않게).
     */
    private suspend fun remember(
        member: Long?,
        head: HeadKey,
        location: String,
        size: Int,
        layers: List<FaceLayer>,
        full: HeadBody? = null
    ) {
        if (member == null || selfId() != member) return
        synchronized(lock) { if (cachedFor == member) hold(location, HeldHead(size, layers)) }
        val store = selfStore ?: return
        if (head.owner != member) return
        withContext(io) {
            // 디스크에 쓰기 직전에 한 번 더 확인한다(계정 바뀜 경쟁).
            if (selfId() != member) return@withContext
            if (full != null) {
                synchronized(lock) { mine.clear() }
                store.saveHead(member, HEAD_PREFIX + location, SelfFaceStore.Head(layers, full.rightLayers, full.meta))
            } else {
                store.saveLayers(member, HEAD_PREFIX + location, layers)
            }
        }
    }

    /** 옛 서버(층 주소 없음): 통짜 응답의 정면 세 층. */
    private suspend fun legacyFront(member: Long?, head: HeadKey, location: String): Outcome<List<FaceLayer>> {
        val result = headBody(head) { body ->
            headOf(body)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Server(MALFORMED))
        }
        val layers = (result as? Outcome.Success)?.value ?: return result
        remember(member, head, location, FaceUse.FULL.size(), layers)
        return result
    }

    /** 정면 세 층 받기 결과 → 층. 하나라도 받지 못했으면 그 실패다(없는 층은 얼굴 없음). */
    private fun frontOf(fetched: List<Fetched>): Outcome<List<FaceLayer>> {
        fetched.forEach { result ->
            when (result) {
                is Fetched.Failed -> return result.failure
                Fetched.Missing, Fetched.Unsupported -> return Outcome.Failure(AppError.Rejected(FACE_IMAGE_NOT_FOUND))
                is Fetched.Body -> Unit
            }
        }
        return Outcome.Success(
            FRONT_LAYERS.zip(fetched) { (_, part), result ->
                FaceLayer(part, (result as Fetched.Body).bytes)
            }
        )
    }

    private fun headUrl(head: HeadKey): HttpUrl.Builder =
        baseUrl.newBuilder().addPathSegments(HEADS_PATH).addPathSegment(head.owner.toString()).addPathSegment(head.job)

    private fun metaUrl(head: HeadKey): HttpUrl = headUrl(head).addPathSegment(META_PATH).build()

    /**
     * 층 하나. 메타가 층 주소를 주면(layerUrls) 그 주소를 그대로 쓰고, 그 크기에 그 층이 없으면 없는 층이다. 메타에 주소가 없으면(옛 서버
     * 메타) 앱이 만든다.
     */
    private suspend fun fetchLayer(head: HeadKey, meta: HeadMeta, name: String, size: Int, isMine: Boolean): Fetched {
        val url = if (meta.hasUrls) {
            meta.urlOf(size, name)?.let(::sameServer) ?: return Fetched.Missing
        } else {
            headUrl(head).addPathSegments(LAYERS_PATH + name).addQueryParameter(SIZE, size.toString()).build()
        }
        return fetchHead(url, isMine)
    }

    /** 메타의 상대 주소를 서버 주소로 푼다. 다른 서버를 가리키면(토큰을 보내지 않게) 쓰지 않는다. */
    private fun sameServer(path: String): HttpUrl? = baseUrl.resolve(path)?.takeIf {
        it.scheme == baseUrl.scheme && it.host == baseUrl.host &&
            it.port == baseUrl.port
    }

    /**
     * 머리 층·메타 하나. 같은 주소를 받는 중이면 그것을 기다린다. 받기는 [fetches]에서 해 부른 화면이 떠나도 끝까지 받는다. 내 머리
     * ([isMine])는 HTTP 캐시에 남기지 않는다(내 얼굴 저장소에 한 벌만).
     */
    private suspend fun fetchHead(url: HttpUrl, isMine: Boolean): Fetched {
        val key = url.toString()
        val pending = synchronized(lock) {
            if (isMine) mine[key]?.let { return Fetched.Body(it) }
            inflight[key] ?: fetches.async { keepMine(key, isMine, download(url, isMine)) }.also { started ->
                inflight[key] = started
                started.invokeOnCompletion {
                    synchronized(lock) { if (inflight[key] === started) inflight.remove(key) }
                }
            }
        }
        return pending.await()
    }

    /** 내 머리 메타·층이면 얻은 본문을 [mine]에 둔다(이어 받기). 회원이 바뀌어 비운 뒤면 두지 않는다. */
    private fun keepMine(key: String, isMine: Boolean, fetched: Fetched): Fetched {
        if (isMine && fetched is Fetched.Body) {
            synchronized(lock) {
                if (cachedFor == null || cachedFor != selfId()) return fetched
                // 머리 하나(메타 + 층 여섯)를 넘게 쌓이면(얼굴이 바뀜) 앞 것을 버린다.
                if (mine.size >= MINE_ENTRIES) mine.clear()
                mine[key] = fetched.bytes
            }
        }
        return fetched
    }

    /** 층·메타 응답 하나. 503 HEAD_BUSY면 한 번 다시 받는다. */
    private suspend fun download(url: HttpUrl, isMine: Boolean): Fetched {
        val first = downloadOnce(url, isMine)
        return if (first is Fetched.Failed && first.isBusy) downloadOnce(url, isMine) else first
    }

    private suspend fun downloadOnce(url: HttpUrl, isMine: Boolean): Fetched = try {
        val request = Request.Builder().url(url).header(ACCEPT, LAYER_ACCEPT)
            .apply { if (isMine) cacheControl(NO_STORE) }
            .build()
        client.newCall(request).await().use { response ->
            withContext(io) {
                when {
                    response.isSuccessful -> bodyOf(response)
                    else -> refusalOf(response.code, response.body.string())
                }
            }
        }
    } catch (e: IOException) {
        Fetched.Failed(Outcome.Failure(AppError.Network))
    }

    private fun bodyOf(response: Response): Fetched =
        if (response.body.contentLength() > MAX_LAYER_BYTES || response.body.source().request(MAX_LAYER_BYTES + 1)) {
            Fetched.Failed(Outcome.Failure(AppError.Server(TOO_LARGE)))
        } else {
            Fetched.Body(response.body.source().readByteArray())
        }

    /** 거부 응답: 404 reason(없는 층·옛 서버·실패), 405 옛 서버, 503 HEAD_BUSY 다시 받기, 그 밖은 실패. */
    private fun refusalOf(status: Int, body: String): Fetched {
        if (status == HTTP_BAD_METHOD) return Fetched.Unsupported
        val error = ApiErrorMapper.fromHttp(status, body)
        val code = (error as? AppError.Rejected)?.code
        return when {
            status == HTTP_NOT_FOUND && code == LAYER_NOT_FOUND -> Fetched.Missing
            status == HTTP_NOT_FOUND && code == NOT_FOUND -> Fetched.Unsupported
            else -> Fetched.Failed(Outcome.Failure(error), isBusy = status == HTTP_UNAVAILABLE && HEAD_BUSY in body)
        }
    }

    /**
     * 예전 통짜 응답 GET /avatar/heads/{회원ID}/{작업ID} 본문을 받아 [parse]로 바꾼다. [MAX_HEAD_BYTES]를 넘으면 실패다. 본문
     * 읽기(최대 32MiB)와 [parse](Base64·JSON)는 [io]에서 한다(부른 쪽이 주 스레드여도 막지 않는다).
     */
    private suspend fun <T> headBody(head: HeadKey, parse: (String) -> Outcome<T>): Outcome<T> {
        val request = Request.Builder().url(headUrl(head).build()).header(ACCEPT, JSON).build()
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

    /** 통짜 응답 JSON. JSON 객체가 아니면 null. */
    private fun headJson(body: String): JsonObject? = try {
        HEAD_JSON.parseToJsonElement(body) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    /** 통짜 응답 JSON의 정면 세 Data URL을 층으로 바꾼다. JSON이 아니거나 층 하나라도 틀리면 null. */
    private fun headOf(body: String): List<FaceLayer>? = headJson(body)?.let(::frontOf)

    private fun frontOf(head: JsonObject): List<FaceLayer>? = HEAD_FIELDS.map { (field, part) ->
        val png = (head[field] as? JsonPrimitive)?.contentOrNull?.let(::pngOf) ?: return null
        FaceLayer(part, png)
    }

    /** 통짜 응답 → 층별 머리: 정면·옆모습 층과 이미지 값을 뺀 합성 정보만 남긴다(큰 본문 문자열을 들고 있지 않는다). */
    private fun legacyHeadOf(body: String): HeadBody {
        val head = headJson(body) ?: return HeadBody(emptyList(), emptyList(), EMPTY_META)
        val right = RIGHT_FIELDS.map { field ->
            (head[field] as? JsonPrimitive)?.contentOrNull?.let(::pngOf)
        }.takeIf { all -> all.none { it == null } }?.filterNotNull().orEmpty()
        val mobile = (head[MOBILE] as? JsonObject)?.let { values ->
            JsonObject(values.filterValues { !((it as? JsonPrimitive)?.contentOrNull.orEmpty().startsWith(DATA_URL)) })
        }
        val meta = JsonObject(if (mobile == null) emptyMap() else mapOf(MOBILE to mobile)).toString()
        return HeadBody(frontOf(head).orEmpty(), right, meta)
    }

    /** "data:image/png;base64,…"의 PNG 바이트. 형식이 다르거나 비었으면 null. */
    private fun pngOf(dataUrl: String): ByteArray? {
        if (!dataUrl.startsWith(DATA_PNG)) return null
        val bytes = try {
            Base64.getDecoder().decode(dataUrl.substring(DATA_PNG.length))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_LAYER_BYTES }
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

    /**
     * 메모리 캐시를 [member]의 것으로 맞춘다. 회원이 바뀌었거나 로그아웃(null)이면 메모리(받는 중 합치기·이어 받기 포함)를 비운다.
     * 디스크(HTTP 캐시의 다른 러너 머리)와 내 얼굴 저장소는 비우지 않는다(S15P31A207-345 2차, 받아 둔 것은 지우지 않는다): 다른 러너
     * 머리는 공용이라 계정과 상관없이 같이 쓰고, 내 얼굴은 저장소가 계정별 칸으로 나눠 다른 계정에 섞이지 않는다. 로그인 상태면 참.
     */
    private fun ownCacheFor(member: Long?): Boolean {
        synchronized(lock) {
            if (cachedFor != member) {
                memory.clear()
                heads.clear()
                inflight.clear()
                mine.clear()
                cachedFor = member
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

    /** 메모리에 둔 머리 정면 세 층과 그 크기. */
    private class HeldHead(val size: Int, val layers: List<FaceLayer>)

    /** 셀카 머리 위치("{회원ID}:{작업ID}"). */
    private class HeadKey(val owner: Long, val job: String) {
        companion object {
            /** 형식이 틀리면(회원 ID가 숫자가 아님, 빈 칸, '/') null. */
            fun of(location: String): HeadKey? {
                val segments = location.split(':')
                if (segments.size != 2 || segments.any { it.isBlank() || it.contains('/') }) return null
                val owner = segments[0].toLongOrNull() ?: return null
                return HeadKey(owner, segments[1])
            }
        }
    }

    /**
     * 메타가 준 층 주소(layerUrls: 크기 → 층 이름 → 상대 주소). [hasUrls]가 거짓이면 옛 서버 메타라 앱이 주소를 만든다. 메타를 읽지
     * 못해도(JSON이 아님) 주소 없음으로 본다.
     */
    private class HeadMeta(private val urls: Map<String, Map<String, String>>?) {
        val hasUrls: Boolean get() = urls != null

        fun urlOf(size: Int, layer: String): String? = urls?.get(size.toString())?.get(layer)

        companion object {
            fun of(bytes: ByteArray): HeadMeta {
                val root = try {
                    HEAD_JSON.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                } catch (e: IllegalArgumentException) {
                    null
                }
                val layerUrls = root?.get(LAYER_URLS) as? JsonObject ?: return HeadMeta(null)
                return HeadMeta(
                    layerUrls.mapValues { (_, layers) ->
                        (layers as? JsonObject).orEmpty().mapNotNull { (name, url) ->
                            (url as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { name to it }
                        }.toMap()
                    }
                )
            }
        }
    }

    /** 층·메타 요청 하나의 결과. */
    private sealed interface Fetched {
        class Body(val bytes: ByteArray) : Fetched

        /** 404 LAYER_NOT_FOUND 또는 메타에 그 층 주소가 없음: 이 머리에 그 층이 없다. */
        data object Missing : Fetched

        /** reason 없는 404·405: 서버가 층 주소를 모른다(예전 서버). */
        data object Unsupported : Fetched

        /** 실패. [isBusy]는 503 HEAD_BUSY(서버가 머리를 적재하느라 바쁘다, 한 번 다시 받는다). */
        class Failed(val failure: Outcome.Failure, val isBusy: Boolean = false) : Fetched
    }

    private companion object {
        const val SELFIE_PREFIX = "selfie:"
        const val HEAD_PREFIX = "head:"
        const val HEADS_PATH = "avatar/heads"
        const val LAYERS_PATH = "layers/"
        const val META_PATH = "meta"
        const val LAYER_URLS = "layerUrls"
        const val SIZE = "size"
        const val JSON = "application/json"
        const val LAYER_ACCEPT = "image/png, application/json"
        const val DATA_PNG = "data:image/png;base64,"
        const val DATA_URL = "data:"
        const val MOBILE = "mobile"
        const val EMPTY_META = "{}"

        const val HTTP_NOT_FOUND = 404
        const val HTTP_BAD_METHOD = 405
        const val HTTP_UNAVAILABLE = 503
        const val NOT_FOUND = "NOT_FOUND"
        const val LAYER_NOT_FOUND = "LAYER_NOT_FOUND"
        const val HEAD_BUSY = "HEAD_BUSY"

        /** 내 머리 요청은 HTTP 캐시에 남기지 않는다(내 얼굴 저장소에 한 벌만). */
        val NO_STORE: CacheControl = CacheControl.Builder().noStore().build()

        /** 예전 통짜 머리 응답 한 개의 상한(정면·옆모습 층의 base64, 합성 정보, 32MiB. 서버 중계 상한과 같다). */
        const val MAX_HEAD_BYTES = 32L * 1024 * 1024

        /** 머리 층 한 장의 상한(4MiB, 1024 PNG 원본). */
        const val MAX_LAYER_BYTES = 4L * 1024 * 1024

        /** 머리 응답을 읽지 못함(앱이 그리지 않음). */
        const val MALFORMED = 502

        /** 주소 층 이름 → 그리는 순서 자리(얼굴 바탕·눈·머리카락). */
        val FRONT_LAYERS = listOf(
            "face" to FacePart.EARS,
            "eyes" to FacePart.EYES,
            "hair" to FacePart.HAIR
        )

        /** 큰 아바타의 옆모습 층(얼굴·눈·머리카락 순서). */
        val SIDE_LAYERS = listOf("rightFace", "rightEyes", "rightHair")

        /** 통짜 응답 항목 → 그리는 순서 자리(얼굴 바탕·눈·머리카락). */
        val HEAD_FIELDS = listOf(
            "faceImage" to FacePart.EARS,
            "eyesImage" to FacePart.EYES,
            "hairImage" to FacePart.HAIR
        )

        /** 통짜 응답의 옆모습 층 항목(얼굴·눈·머리카락 순서). */
        val RIGHT_FIELDS = listOf("rightFaceImage", "rightEyesImage", "rightHairImage")
        val HEAD_JSON = Json { ignoreUnknownKeys = true }
        const val PARTS_PATH = "avatar/parts"
        const val FACE_IMAGE_NOT_FOUND = "FACE_IMAGE_NOT_FOUND"
        const val ACCEPT = "Accept"
        const val AGE = "Age"
        const val PNG = "image/png"
        const val MEMORY_ENTRIES = 40

        /** 메모리에 두는 셀카 머리 수(머리 하나는 정면 세 층 PNG, 수백 KB~수 MB). */
        const val HEAD_ENTRIES = 12

        /** 이어 받기로 메모리에 두는 내 머리 파일 수 상한(메타 1 + 층 6). */
        const val MINE_ENTRIES = 7
        const val LOAD_FACTOR = 0.75f
        const val MILLIS_PER_SECOND = 1_000L

        /** 부위 그림 한 장의 상한(2MiB). */
        const val MAX_PART_BYTES = 2L * 1024 * 1024

        /** 상한을 넘은 응답(앱이 받지 않음). */
        const val TOO_LARGE = 413
    }
}
