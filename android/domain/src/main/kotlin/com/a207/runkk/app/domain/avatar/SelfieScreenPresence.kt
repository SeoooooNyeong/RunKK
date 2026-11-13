package com.a207.runkk.app.domain.avatar

/**
 * 얼굴 만들기 화면이 지금 보이는가(design-oo 05-30 [임시:D-성-120]). 화면이 보이면(ON_START) [opened], 안 보이면(ON_STOP)
 * [closed]를 부른다. 결과 알림 구현이 보이는 동안에는 알림을 내지 않는다. 구현은 data.push SelfieNoticeTracker다.
 */
interface SelfieScreenPresence {
    val isVisible: Boolean

    fun opened()

    fun closed()
}
