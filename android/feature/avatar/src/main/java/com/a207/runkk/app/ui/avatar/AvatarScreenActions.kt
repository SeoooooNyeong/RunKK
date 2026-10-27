package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/** 아바타 꾸미기(J3) 사용자 행동 묶음: 부위·배경 탭, 입기·벗기, 배경 고르기, 얼굴 바꾸기, 상점, 저장. */
data class AvatarScreenActions(
    val onBack: () -> Unit = {},
    val onShop: () -> Unit = {},
    val onFace: () -> Unit = {},
    val onSlot: (ItemSlot) -> Unit = {},
    val onBackgroundTab: () -> Unit = {},
    val onToggle: (Shop.Item) -> Unit = {},
    val onChooseBackground: (AvatarBackgrounds.Item) -> Unit = {},
    val onChooseDefaultBackground: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onBackgroundMessageShown: () -> Unit = {},
    val onSave: () -> Unit = {}
)
