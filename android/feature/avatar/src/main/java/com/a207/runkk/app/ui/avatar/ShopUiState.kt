package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/**
 * 상점(피그마 제안 A안 입어보기 무대 + 3열, 62333:1796) 상태. 칸을 고르면 위 무대에 [selected]를 입혀 본다(저장하지 않는다).
 * 고른 칸이 가진 것이 아니면 아래 구매 단추, 가졌으면 입기 단추가 뜬다.
 */
data class ShopUiState(
    val isLoading: Boolean = true,
    /** 고른 부위(상의·하의·신발·모자 칩, 처음 상의). */
    val slot: ItemSlot = ItemSlot.TOP,
    /** 상점 전체 목록(부위는 화면이 거른다, 입어 보기가 다른 부위 착용 그림 키도 쓴다). */
    val shop: Shop? = null,
    /** 무대에 그릴 내 아바타(착용·얼굴·배경). 못 읽으면 null이고 무대는 로딩 표시만 둔다. */
    val avatar: Avatar? = null,
    val look: AvatarLook = AvatarLook(),
    val faceLayers: List<FaceLayer> = emptyList(),
    /** 입어 보는 칸. */
    val selected: Shop.Item? = null,
    val confirming: Shop.Item? = null,
    val isBuying: Boolean = false,
    val isEquipping: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val itemsInSlot: List<Shop.Item> get() = shop?.items.orEmpty().filter { it.slot == slot }

    fun isEquipped(item: Shop.Item): Boolean = avatar?.equipped?.get(item.slot) == item.itemId

    /** 무대 착용: 지금 착용에 입어 보는 칸을 얹는다. */
    val tryOn: Map<ItemSlot, Long>
        get() {
            val equipped = avatar?.equipped.orEmpty()
            return selected?.let { equipped + (it.slot to it.itemId) } ?: equipped
        }
}
