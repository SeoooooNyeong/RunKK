package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/**
 * 아바타 꾸미기(피그마 제안 A안) 사용자 행동 묶음: 부위·배경 탭, 입어 보기·벗기, 되돌리기, 배경 고르기, 상점, 저장. 얼굴 바꾸기는 내 러너
 * 목록에서 들어간다(피그마 A안 설명 2).
 */
data class AvatarScreenActions(
    val onBack: () -> Unit = {},
    val onShop: () -> Unit = {},
    val onSlot: (ItemSlot) -> Unit = {},
    val onBackgroundTab: () -> Unit = {},
    val onToggle: (Shop.Item) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onChooseBackground: (AvatarBackgrounds.Item) -> Unit = {},
    val onChooseDefaultBackground: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onBackgroundMessageShown: () -> Unit = {},
    val onSave: () -> Unit = {}
)
