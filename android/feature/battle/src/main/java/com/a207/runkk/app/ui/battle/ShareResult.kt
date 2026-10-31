package com.a207.runkk.app.ui.battle

/** 공유 화면을 연 결과. */
enum class ShareResult {
    /** 카카오톡 공유 화면을 열었다. */
    KAKAO_TALK,

    /** 카카오톡이 없어 카카오톡 웹 공유(브라우저)를 열었다. */
    WEB,

    /** 카카오톡도 브라우저도 열지 못했다. */
    FAILED
}
