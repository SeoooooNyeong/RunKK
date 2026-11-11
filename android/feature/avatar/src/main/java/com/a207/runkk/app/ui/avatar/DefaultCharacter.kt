package com.a207.runkk.app.ui.avatar

/**
 * 서버 기본 캐릭터 값(DefaultCharacter 4종, 서버 계약 그대로). 앱은 어느 값이든 기본 얼굴 하나(표시 성별의 기본 머리)로 그리고, 「기본 얼굴」을
 * 고르면 [DEFAULT]를 저장한다([임시:D-성-187]). 동물 이름·그림은 보이지 않는다.
 */
enum class DefaultCharacter {
    RABBIT,
    BEAR,
    FOX,
    CAT;

    companion object {
        /** 「기본 얼굴」을 고를 때 서버에 저장하는 값(기본 캐릭터 1번, FR-155·FR-158). */
        val DEFAULT: DefaultCharacter = RABBIT
    }
}
