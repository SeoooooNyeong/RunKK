package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.Outcome

/**
 * 아바타·상점 포트(5.7.1 /avatar/…, /shop/…). 구현 api.avatar.
 * 셀카는 앱이 긴 변 1024px·JPEG·1MB 이하로 줄여 보내고 어디에도 저장하지 않는다(D-23, D-59).
 */
interface AvatarRepository {
    suspend fun avatar(): Outcome<Avatar>

    suspend fun createFromSelfie(jpeg: ByteArray): Outcome<Avatar>

    suspend fun chooseDefault(characterId: String): Outcome<Avatar>

    /** 착용·해제 변경. 거부된 항목은 Avatar.rejected에 사유로 모인다. */
    suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar>

    suspend fun shop(slot: ItemSlot?): Outcome<Shop>

    suspend fun purchase(itemId: Long): Outcome<Int>
}

/** 착용 부위(서버 common.ItemSlot). */
enum class ItemSlot {
    TOP,
    BOTTOM,
    SHOES,
    HAT
}

/**
 * 아바타(서버 avatar.AvatarView, FR-155~FR-163). 얼굴이 없으면 faceKey가 null이고 "기본 캐릭터를 고를 수 있다".
 * balance는 리워드 잔액이다. 필드는 10/21 API 계약으로 확정한다.
 */
data class Avatar(
    val faceKey: String?,
    val defaultCharacterId: String?,
    val bodyShape: String,
    val skinTone: String,
    val equipped: Map<ItemSlot, Long>,
    val balance: Int,
    val canCreateSelfieToday: Boolean,
    val rejected: Map<ItemSlot, String>
)

/** 상점(서버 reward.ShopView, FR-160): 판매 중 아이템과 이미 보유한 아이템. */
data class Shop(val items: List<Item>, val balance: Int) {
    data class Item(
        val itemId: Long,
        val slot: ItemSlot,
        val name: String,
        val price: Int,
        val owned: Boolean,
        val onSale: Boolean
    )
}
