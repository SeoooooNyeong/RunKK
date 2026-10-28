package com.a207.runkk.app.domain.run

import kotlinx.coroutines.flow.Flow

/** 워치 통신 포트. 구현 data.watch.WatchLink(D-20). RunWatchBridge만 부른다(D-95). */
interface WatchGateway {
    fun publish(d: WatchDisplay)

    val startRequests: Flow<Unit>

    /** 워치의 종료 요청(길게 누름). RunTracker가 화면의 길게 누르기 종료와 같은 규칙으로 처리한다. */
    val endRequests: Flow<Unit>

    /** 워치의 일시정지·재개 요청(PauseRequestMessage, FR-194). 모르는 action은 구현이 버리고 경고 로그를 남긴다(D-157 (6)). */
    val pauseRequests: Flow<PauseAction>

    val heartRates: Flow<List<HeartRateSample>>

    fun isConnected(): Boolean

    /** 근접 알림(ProximityMessage, FR-196). null이면 끔(isActive 거짓). */
    fun publishProximity(alert: ProximityAlert?)

    /** 추월·역전 순간 1초 긴 진동(LeadChangeMessage, FR-188). [kind]는 Guidance.Kind.OVERTOOK 또는 OVERTAKEN이다. */
    fun publishLeadChange(kind: Guidance.Kind)

    /** 워치 일시정지 요청의 동작(wear.contract PauseRequestMessage.Action과 같은 값). domain은 wear.contract를 모른다(5.1.2). */
    enum class PauseAction {
        PAUSE,
        RESUME
    }
}
