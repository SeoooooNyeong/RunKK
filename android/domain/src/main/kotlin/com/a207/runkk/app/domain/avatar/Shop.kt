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
        val isOwned: Boolean,
        val isOnSale: Boolean,
        /** 아이템 그림 키(서버 assetKey, W8). 없거나 앱에 없는 키면 부위 기본 그림이다. */
        val imageKey: String? = null,
        /** 해금 조건을 아직 못 채워 살 수 없다(W8). */
        val isLocked: Boolean = false,
        /** 해금 조건 문구(서버 unlockCondition, 예 "하프 완주하면 열려요"). 조건이 없으면 null. */
        val unlockLabel: String? = null
    )
}
