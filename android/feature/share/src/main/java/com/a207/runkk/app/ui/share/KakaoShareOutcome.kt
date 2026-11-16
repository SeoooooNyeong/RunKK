package com.a207.runkk.app.ui.share

/** 카카오톡 공유 결과([KakaoLinkSharer]). */
enum class KakaoShareOutcome {
    /** 카카오톡을 열었다. */
    KAKAO_TALK,

    /** 카카오톡이 없어 카카오톡 웹 공유를 브라우저로 열었다. */
    WEB,

    /** 열지 못했다(카카오 SDK 미초기화, 보내기 실패, 열 브라우저 없음). 화면이 「카카오톡을 열 수 없어요」를 띄운다. */
    FAILED
}
