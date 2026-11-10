package com.a207.runkk.app.domain.avatar

/**
 * 셀카 작업 결과를 기기 알림 한 건으로 알린다(design-oo 05-30 [임시:D-성-120], [임시:FR-성-21]). 워커가 부르고, 구현(data.push
 * SelfieNoticeTracker)이 앱이 앞에 있거나 얼굴 만들기 화면이 보이거나 알림 권한이 없으면 내지 않는다. 같은 작업은 한 번만 낸다.
 */
interface SelfieResultNotifier {
    fun notify(jobId: String, succeeded: Boolean)
}
