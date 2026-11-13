package com.a207.runkk.app.ui.avatar

/**
 * 내 아바타 준비 상태(design-oo 05-30 [임시:D-성-103], 3절 상태도). [MyAvatarStore]가 낸다.
 * Idle(로그인 전·로그아웃 뒤) → Loading(성별 기다림·입력 읽는 중) → Ready 또는 Failed.
 */
sealed interface MyAvatarState {
    /** 아직 데우지 않았거나 로그아웃으로 비웠다. */
    data object Idle : MyAvatarState

    /** 성별을 모르거나 입력을 읽는 중이다. 칸은 배경 자리와 로딩 표시를 보인다. */
    data object Loading : MyAvatarState

    /** 그릴 모습이 모였다. */
    data class Ready(val look: MyAvatarLook) : MyAvatarState

    /** 아바타·내 러너를 읽지 못했다. 칸은 성별 기본 그림을 보이고, 다음 칸 진입이나 refresh에서 다시 시도한다. */
    data object Failed : MyAvatarState
}
