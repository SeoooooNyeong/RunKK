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
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

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
