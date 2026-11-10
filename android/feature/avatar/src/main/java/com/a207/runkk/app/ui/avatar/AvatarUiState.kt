package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

data class AvatarUiState(
    val isLoading: Boolean = true,
    val avatar: Avatar? = null,
    /** 셀카 얼굴 부위 그림. 셀카 얼굴이 아니거나 받지 못하면 비어 있고 자리 그림을 그린다. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /** Rive 렌더러 값(의상·체형·러닝량·셀카 머리, W9). */
    val look: AvatarLook = AvatarLook(),
    /** 보유 아이템 이름을 찾으려고 읽은 상점 목록(보유 여부 포함). */
    val items: List<Shop.Item> = emptyList(),
    val slot: ItemSlot = ItemSlot.TOP,
    /** 배경 탭(랜드마크 배경 고르기)을 보고 있는가. 거짓이면 [slot] 부위 탭. */
    val isBackgroundTab: Boolean = false,
    val isSaving: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val ownedInSlot: List<Shop.Item> get() = items.filter { it.isOwned && it.slot == slot }

    fun isEquipped(item: Shop.Item): Boolean = avatar?.equipped?.get(item.slot) == item.itemId

    fun itemName(itemId: Long?): String? = itemId?.let { id -> items.firstOrNull { it.itemId == id }?.name }
}
