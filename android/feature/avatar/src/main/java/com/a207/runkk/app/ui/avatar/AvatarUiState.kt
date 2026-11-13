package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/**
 * 아바타 꾸미기(피그마 제안 A안 62337:1814) 상태. 칸을 누르면 [draft](입어 볼 착용)만 바뀌어 무대에 바로 입혀 보이고, 「저장」을 눌러야
 * 서버에 보낸다. 되돌리기는 [draft]를 들어올 때의 착용([Avatar.equipped])으로 돌린다.
 */
data class AvatarUiState(
    val isLoading: Boolean = true,
    val avatar: Avatar? = null,
    /** 셀카 얼굴 부위 그림. 셀카 얼굴이 아니거나 받지 못하면 비어 있고 자리 그림을 그린다. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /** Rive 렌더러 값(의상·체형·러닝량·셀카 머리, W9). 의상은 [draft]를 따른다. */
    val look: AvatarLook = AvatarLook(),
    /** 상점 목록(보유·잠김 포함). */
    val items: List<Shop.Item> = emptyList(),
    /** 입어 볼 착용(부위 → 아이템 ID). 저장 전 미리보기다. */
    val draft: Map<ItemSlot, Long> = emptyMap(),
    val slot: ItemSlot = ItemSlot.TOP,
    /** 배경 탭(랜드마크 배경 고르기)을 보고 있는가. 거짓이면 [slot] 부위 탭. */
    val isBackgroundTab: Boolean = false,
    val isSaving: Boolean = false,
    /** 저장을 마쳤다(화면을 닫는다). */
    val isDone: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 부위 칸: 가진 아이템과 아직 잠긴 아이템(조건 표시). 살 수 있는 것은 마지막 「상점에서 더 보기」 칸이 맡는다. */
    val cellsInSlot: List<Shop.Item> get() = items.filter { it.slot == slot && (it.isOwned || it.isLocked) }

    /** 저장하지 않은 착용 변경이 있는가. */
    val isDirty: Boolean get() = avatar != null && draft != avatar.equipped

    fun isEquipped(item: Shop.Item): Boolean = draft[item.slot] == item.itemId
}
