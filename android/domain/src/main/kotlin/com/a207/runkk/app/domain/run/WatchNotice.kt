package com.a207.runkk.app.domain.run

/** 워치 표시 정보에 싣는 안내 키(2차 리뷰 M-3). data.watch가 문자열 리소스로 바꿔 워치로 보낸다. */
enum class WatchNotice {
    /** 이미 측정 중이라 워치 시작 요청을 버렸다. */
    ALREADY_MEASURING,

    /** 러닝 화면 구독자가 없어 워치 시작 요청을 버렸다. */
    OPEN_RUN_SCREEN,

    /** 대결·크루런은 폰에서만 끝낼 수 있다. */
    END_ON_PHONE
}
