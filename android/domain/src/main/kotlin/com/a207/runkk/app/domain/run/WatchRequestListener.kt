package com.a207.runkk.app.domain.run

/**
 * 워치에서 온 요청을 받는다(D-95). RunWatchBridge.bind가 받고 RunTracker의 내부 구현이 받는다.
 * 요청 종류마다 연산을 나눈다(D-36): 정지·재개는 폰 버튼·알림 버튼과 같은 RunSession.pause·resume으로 간다(D-256).
 */
interface WatchRequestListener {
    fun onStartRequested()

    fun onEndRequested()

    fun onPauseRequested()

    fun onResumeRequested()

    fun onHeartRates(samples: List<HeartRateSample>)
}
