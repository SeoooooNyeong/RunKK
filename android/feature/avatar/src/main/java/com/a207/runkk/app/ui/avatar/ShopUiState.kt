package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

data class ShopUiState(
    val isLoading: Boolean = true,
    /** 고른 부위(J4는 상의·하의·신발·모자 탭, 처음 상의). null이면 전체. */
    val slot: ItemSlot? = ItemSlot.TOP,
    val shop: Shop? = null,
    val confirming: Shop.Item? = null,
    val isBuying: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)
