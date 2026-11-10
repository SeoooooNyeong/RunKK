package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.avatar.GeneratedHeadLayers
import com.a207.runkk.app.core.avatar.RunkkRiveAvatar
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Rive 렌더러(W9, S15P31A207-300)에 넘길 내 아바타 값(contract-W8.md 6.1). 성별은 그리는 자리에서 [LocalAvatarCharacter]로 정한다.
 *
 * @property outfit 상점 착용을 Rive 의상 번호로 옮긴 값([outfitOf])
 * @property bodyShape 서버 bodyVariant(0..2)를 옮긴 값([bodyShapeOf])
 * @property runAmount 서버 runAmount(0..100). 피부 밝기에만 쓴다
 * @property headLayers 셀카 머리 레이어. 없거나 만들지 못하면 null(기본 머리)
 */
data class AvatarLook(
    val outfit: AvatarOutfit = AvatarOutfit(),
    val bodyShape: AvatarBodyShape = AvatarBodyShape.BASE,
    val runAmount: Int = 0,
    val headLayers: GeneratedHeadLayers? = null
)

/** 아바타(착용·체형)와 상점 목록, 러닝량으로 렌더러 값을 고친다. 셀카 머리는 그대로 둔다. */
internal fun AvatarLook.withAvatar(avatar: Avatar, items: List<Shop.Item>, runAmount: Int): AvatarLook = copy(
    outfit = outfitOf(avatar.equipped, items),
    bodyShape = bodyShapeOf(bodyVariantOf(avatar.bodyShape)),
    runAmount = runAmount
)

/** 서버 bodyVariant(0 기본, 1 변화 1, 2 변화 2)를 Rive 체형으로. 모르는 값은 기본이다. */
internal fun bodyShapeOf(variant: Int?): AvatarBodyShape =
    AvatarBodyShape.entries.firstOrNull { it.stage == variant } ?: AvatarBodyShape.BASE

/**
 * 상점 착용(부위 → 아이템 ID)을 Rive 의상 번호(상의·하의·신발 0..6)로 옮긴다(contract-W8.md 6.3 제안표). 아이템 그림 키로 찾고, 안 입었거나
 * 표에 없는 키면 0(기본 의상)이다. 모자는 Rive 슬롯이 없어 그리지 않는다.
 */
internal fun outfitOf(equipped: Map<ItemSlot, Long>, items: List<Shop.Item>): AvatarOutfit {
    fun idOf(slot: ItemSlot): Int {
        val key = equipped[slot]?.let { id -> items.firstOrNull { it.itemId == id }?.imageKey } ?: return 0
        return RIVE_OUTFITS[slot]?.indexOf(key)?.takeIf { it >= 0 }?.plus(1) ?: 0
    }
    return AvatarOutfit(top = idOf(ItemSlot.TOP), bottom = idOf(ItemSlot.BOTTOM), shoes = idOf(ItemSlot.SHOES))
}

/** Rive 의상 1..6에 대응하는 그림 키(서버 V62 기본 목록, contract-W8.md 6.3). 0은 기본 의상이라 표에 없다. */
private val RIVE_OUTFITS: Map<ItemSlot, List<String>> = mapOf(
    ItemSlot.TOP to listOf(
        "top_lime_singlet",
        "top_dawn_windbreaker",
        "top_crew_tee",
        "top_winter_hoodie",
        "top_cherry_vest",
        "top_finisher_tee"
    ),
    ItemSlot.BOTTOM to listOf("bottom_running_shorts", "bottom_jogger_pants", "bottom_leggings"),
    ItemSlot.SHOES to listOf("shoes_basic_runner", "shoes_lime_runner", "shoes_carbon_racer")
)

/**
 * 아바타 화면이 그리는 얼굴 하나.
 *
 * @property faceLayers Rive를 그리지 못할 때 앞 단계 그림이 쓰는 대체 그림(셀카 부위 또는 셀카 머리 정면 세 층)
 * @property headLayers 렌더러 셀카 머리 레이어. 셀카 머리가 아니거나 만들지 못했으면 null(기본 머리)
 */
class FaceLook(val faceLayers: List<FaceLayer>, val headLayers: GeneratedHeadLayers?) {
    internal companion object {
        val NONE = FaceLook(emptyList(), null)
    }
}

/**
 * 얼굴 키마다 [FaceLook]을 한 번 만들어 최근 [ENTRIES]개를 프로세스에 두고 내 러너·꾸미기 화면이 나눠 쓴다(리뷰 M1).
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
        /** 둘 얼굴 수(내 얼굴과 막 바꾼 얼굴 정도). 머리 레이어가 크므로 작게 둔다. */
        const val ENTRIES = 3
        const val LOAD_FACTOR = 0.75f
    }
}

/** 셀카 머리 얼굴 키("head:{회원ID}:{작업ID}")인가. */
internal fun isSelfieHead(faceKey: String?): Boolean = faceKey?.startsWith("head:") == true

/** 내 성별 몸체(FR-162: 여성이면 여 몸체, 그 밖은 남 몸체). */
@Composable
internal fun myAvatarGender(): AvatarGender =
    if (LocalAvatarCharacter.current == AvatarCharacter.FEMALE) AvatarGender.FEMALE else AvatarGender.MALE

/**
 * 내 아바타 그림: Rive 렌더러로 그리고, 네이티브 Rive를 읽지 못하면(시험 환경·기기 문제) [fallback](앞 단계 그림)으로 바꾼다.
 * 배경은 부르는 쪽이 깐다.
 */
@Composable
internal fun MyRiveAvatar(
    look: AvatarLook,
    modifier: Modifier = Modifier,
    motion: AvatarMotion = AvatarMotion.STAND,
    fallback: @Composable () -> Unit
) {
    val gender = myAvatarGender()
    var isFailed by remember(gender, look.headLayers) { mutableStateOf(false) }
    Box(modifier) {
        if (isFailed) {
            fallback()
        } else {
            RunkkRiveAvatar(
                gender = gender,
                motion = motion,
                outfit = look.outfit,
                bodyShape = look.bodyShape,
                runningAmount = look.runAmount,
                headLayers = look.headLayers,
                modifier = Modifier.fillMaxSize(),
                onError = { isFailed = true }
            )
        }
    }
}

/** 앞 단계 기본 아바타 그림(Rive를 그리지 못할 때). */
@Composable
internal fun LegacyStandingAvatar(faceKey: String?, faceLayers: List<FaceLayer>) {
    RunkkAvatar(faceKey, AvatarExpression.STAND, Modifier.fillMaxSize(), faceLayers)
}
