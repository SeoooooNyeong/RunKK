package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome

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
