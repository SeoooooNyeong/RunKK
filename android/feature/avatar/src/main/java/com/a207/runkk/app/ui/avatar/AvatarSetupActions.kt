package com.a207.runkk.app.ui.avatar

/** 얼굴 만들기(피그마 AV-7 1안) 사용자 행동: 고르기, 사진 확인, 만드는 중, 새 얼굴, 실패 화면의 단추. */
data class AvatarSetupActions(
    val onBack: () -> Unit = {},
    val onSelfie: () -> Unit = {},
    val onAlbum: () -> Unit = {},
    val onFaceConsent: () -> Unit = {},
    val onCharacter: (DefaultCharacter) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRepick: () -> Unit = {},
    val onStart: () -> Unit = {},
    /** 「다른 화면 둘러보기」·뒤로: 화면을 떠나도 작업은 이어진다. */
    val onLater: () -> Unit = {},
    val onDoneConfirm: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUseDefault: () -> Unit = {}
)
