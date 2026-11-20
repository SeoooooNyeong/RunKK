package com.a207.runkk.app.ui.avatar

import android.content.Context
import com.a207.runkk.app.core.avatar.AvatarLookDisk
import com.a207.runkk.app.core.avatar.AvatarPrepareTrace
import com.a207.runkk.app.core.avatar.GeneratedHeadLayers
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.SelfieHead
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 얼굴 키마다 [FaceLook]을 한 번 만들어 최근 [ENTRIES]개를 프로세스에 두고 내 러너·꾸미기 화면과 랭킹 시상대 전신 정지 그림이 나눠
 * 쓴다(리뷰 M1, S15P31A207-300).
 * - 셀카 머리("head:…")는 GET /avatar/heads 본문을 한 번만 받아 대체 그림과 머리 레이어를 같은 본문에서 만든다. 머리 레이어는 그림을
 *   풀고 다시 묶어 무거우므로 [worker]에서 만든다.
 * - 받지 못하면(연결·서버 실패, 취소) 두지 않아 다음에 다시 받는다. 받았지만 형식이 틀려 만들지 못하면 기본 머리로 두고 다시 받지 않는다.
 * - 같은 키를 두 화면이 함께 찾으면 한쪽이 받는 동안 기다렸다가 같은 값을 쓴다(받지 못했으면 둘 다 기본 머리, 다음에 다시).
 * - 서로 다른 키는 함께 받는다(AR-89): 잠금은 캐시와 받는 중 표를 고칠 때만 잡고 네트워크를 기다리는 동안은 잡지
 *   않아, 랭킹 시상대 세 러너의 머리가 차례로 받아지지 않는다.
 * - 머리 레이어 기기 사본(S15P31A207-345 9회차): [disk]가 준 칸(내 머리는 회원 칸, 다른 러너는 공용 칸)에 머리 레이어를 남겨, 앱을 다시
 *   켜도 같은 머리(같은 층·메타)면 그림을 풀고 다시 묶지 않는다. 구간 표식 AvatarPrepare head-fetch·head-layers를 남긴다.
 */
@Singleton
class SelfieHeads internal constructor(
    private val avatars: AvatarRepository,
    private val worker: CoroutineDispatcher,
    /** 얼굴 키 → 그 머리 레이어를 남길 기기 칸. null이면 남기지 않는다. */
    private val disk: (String) -> AvatarLookDisk? = { null }
) {
    @Inject
    constructor(avatars: AvatarRepository, accounts: AccountRepository, @ApplicationContext context: Context) : this(
        avatars,
        Dispatchers.Default,
        { faceKey -> diskOf(context, accounts.selfMemberId(), faceKey) }
    )

    /** 받아 둔 [faceKey] 얼굴(기다리지 않는다). 아직 없으면 null. 셀카 머리가 아니면 빈 얼굴이다. */
    fun peek(faceKey: String?): FaceLook? {
        if (faceKey == null) return FaceLook.NONE
        return synchronized(lock) { cache[faceKey] }
    }

    /** [cache]와 [inflight]를 지키는 잠금. 고칠 때만 잠깐 잡는다(받는 동안은 잡지 않는다). */
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, FaceLook>(ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FaceLook>?): Boolean = size > ENTRIES
    }

    /** 받는 중인 키 → 그 받기의 결과(받지 못하면 null). 같은 키를 함께 찾은 쪽은 새로 받지 않고 이것을 기다린다. */
    private val inflight = HashMap<String, CompletableDeferred<FaceLook?>>()

    /**
     * [faceKey]의 얼굴. 얼굴이 없거나 받지 못하면 빈 얼굴(기본 캐릭터)이다. 같은 키를 받던 앞 호출이 취소되거나 실패해 값 없이 끝나면
     * 기다리던 쪽은 기본 머리로 끝내지 않고 한 번 직접 받는다(통합 리뷰 낮음 8).
     */
    suspend fun look(faceKey: String?): FaceLook = look(faceKey, isRetry = false)

    private suspend fun look(faceKey: String?, isRetry: Boolean): FaceLook {
        if (faceKey == null) return FaceLook.NONE
        val mine = CompletableDeferred<FaceLook?>()
        val other = synchronized(lock) {
            cache[faceKey]?.let { return it }
            inflight[faceKey] ?: null.also { inflight[faceKey] = mine }
        }
        if (other != null) {
            other.await()?.let { return it }
            return if (isRetry) FaceLook.NONE else look(faceKey, isRetry = true)
        }
        var loaded: FaceLook? = null
        try {
            loaded = load(faceKey)
        } finally {
            // 끝나면(실패·취소 포함) 표에서 빼고 기다리던 쪽을 깨운다. 받지 못한 것은 두지 않아 다음에 다시 받는다.
            synchronized(lock) {
                loaded?.let { cache[faceKey] = it }
                inflight.remove(faceKey)
            }
            mine.complete(loaded)
        }
        return loaded ?: FaceLook.NONE
    }

    private suspend fun load(faceKey: String): FaceLook? {
        if (!isSelfieHead(faceKey)) return avatars.faceLayers(faceKey).getOrNull()?.let { FaceLook(it, null) }
        val fetchAt = AvatarPrepareTrace.now()
        val head = avatars.selfieHead(faceKey).getOrNull() ?: return null
        AvatarPrepareTrace.mark("head-fetch", fetchAt)
        return FaceLook(head.faceLayers, withContext(worker) { headLayersOf(faceKey, head) })
    }

    private fun headLayersOf(faceKey: String, head: SelfieHead): GeneratedHeadLayers? = try {
        val front = FRONT_PARTS.map { part -> head.faceLayers.firstOrNull { it.part == part }?.png ?: return null }
        if (head.rightLayers.size != SIDE_LAYERS) return null
        GeneratedHeadLayers.fromLayers(
            disk = disk(faceKey),
            faceKey = faceKey,
            face = front[0],
            eyes = front[1],
            hair = front[2],
            rightFace = head.rightLayers[0],
            rightEyes = head.rightLayers[1],
            rightHair = head.rightLayers[2],
            metaJson = head.meta
        )
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

        /** 정면 세 층(얼굴·눈·머리카락) 자리. */
        val FRONT_PARTS = listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR)

        /** 옆모습 층 수(얼굴·눈·머리카락). */
        const val SIDE_LAYERS = 3

        /** 내 머리("head:{나}:…")는 내 회원 칸, 다른 러너 머리는 공용 칸. 로그아웃 상태면 공용 칸. */
        fun diskOf(context: Context, member: Long?, faceKey: String): AvatarLookDisk =
            if (member != null && faceKey.startsWith("head:$member:")) {
                AvatarLookDisk.member(context, member)
            } else {
                AvatarLookDisk.shared(context)
            }
    }
}
