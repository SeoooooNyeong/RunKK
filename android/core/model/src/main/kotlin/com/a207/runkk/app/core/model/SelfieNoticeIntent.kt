package com.a207.runkk.app.core.model

/**
 * 셀카 얼굴 결과 알림(data.push SelfieNoticeTracker)이 앱을 여는 실행 인텐트의 약속(design-oo 05-30 [임시:D-성-162]). 알림을 만드는 쪽과
 * 읽는 쪽(app MainActivity)이 함께 본다([RunNoticeExtras]와 같은 자리). 앱 안 전용 동작이라 다른 앱이 보낸 실행과 섞이지 않는다.
 */
object SelfieNoticeIntent {
    /** 알림을 눌러 얼굴 만들기(결과 단계)를 연다. */
    const val ACTION_OPEN_RESULT = "com.a207.runkk.action.OPEN_SELFIE_RESULT"
}
