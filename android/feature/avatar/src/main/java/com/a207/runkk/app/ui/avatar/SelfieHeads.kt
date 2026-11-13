package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.GeneratedHeadLayers
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 얼굴 키마다 [FaceLook]을 한 번 만들어 최근 [ENTRIES]개를 프로세스에 두고 내 러너·꾸미기 화면과 랭킹 시상대 전신 정지 그림이 나눠
 * 쓴다(리뷰 M1, S15P31A207-300).
 * - 셀카 머리("head:…")는 GET /avatar/heads 본문을 한 번만 받아 대체 그림과 머리 레이어를 같은 본문에서 만든다. 머리 레이어는 그림을
 *   풀고 다시 묶어 무거우므로 [worker]에서 만든다.
 * - 받지 못하면(연결·서버 실패, 취소) 두지 않아 다음에 다시 받는다. 받았지만 형식이 틀려 만들지 못하면 기본 머리로 두고 다시 받지 않는다.
 * - 같은 키를 두 화면이 함께 찾으면 한쪽이 받는 동안 기다렸다가 같은 값을 쓴다.
 */
@Singleton
class SelfieHeads internal constructor(private val avatars: AvatarRepository, private val worker: CoroutineDispatcher) {
    @Inject
    constructor(avatars: AvatarRepository) : this(avatars, Dispatchers.Default)

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, FaceLook>(ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FaceLook>?): Boolean = size > ENTRIES
    }

    /** [faceKey]의 얼굴. 얼굴이 없거나 받지 못하면 빈 얼굴(기본 캐릭터)이다. */
    suspend fun look(faceKey: String?): FaceLook {
        if (faceKey == null) return FaceLook.NONE
        return mutex.withLock {
            cache[faceKey] ?: load(faceKey)?.also { cache[faceKey] = it } ?: FaceLook.NONE
        }
    }

    private suspend fun load(faceKey: String): FaceLook? {
        if (!isSelfieHead(faceKey)) return avatars.faceLayers(faceKey).getOrNull()?.let { FaceLook(it, null) }
        val head = avatars.selfieHead(faceKey).getOrNull() ?: return null
        return FaceLook(head.faceLayers, withContext(worker) { headLayersOf(head.json) })
    }

    private fun headLayersOf(json: String): GeneratedHeadLayers? = try {
        GeneratedHeadLayers.fromHeadJson(json)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 형식 오류(IllegalArgumentException)뿐 아니라 그림 풀기 실패도 기본 머리로 그린다.
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private companion object {
        /**
         * 둘 얼굴 수: 내 얼굴과 막 바꾼 얼굴, 랭킹 시상대 다른 러너 셋(S15P31A207-300)에 한 자리 여유. 머리 레이어가 크므로 이보다
         * 늘리지 않는다(4위부터 얼굴 원은 머리 레이어가 아니라 부위 그림을 따로 받는다).
         */
        const val ENTRIES = 6
        const val LOAD_FACTOR = 0.75f
    }
}
