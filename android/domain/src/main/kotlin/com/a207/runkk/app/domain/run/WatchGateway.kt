package com.a207.runkk.app.domain.run

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 워치 통신 포트. 구현 data.watch.WatchLink(D-20). RunWatchBridge만 부른다(D-95). */
interface WatchGateway {
    fun publish(d: WatchDisplay)

    val startRequests: Flow<Unit>

    /** 워치의 종료 요청(길게 누름). RunTracker가 화면의 길게 누르기 종료와 같은 규칙으로 처리한다. */
    val endRequests: Flow<Unit>

    /** 워치의 일시정지·재개 요청(PauseRequestMessage, FR-194). 모르는 action은 구현이 버리고 경고 로그를 남긴다(D-157 (6)). */
    val pauseRequests: Flow<PauseAction>

    val heartRates: Flow<List<HeartRateSample>>

    /**
     * 동행 워치가 잰 손목 케이던스(S15P31A207-242). 심박과 같은 허용 규칙(심박 동의·워치연동)을 따른다. 워치 단독 측정 구현은 운동 세션을
     * RunTracker.onMotionSample로 바로 넘기므로 비어 있다.
     */
    val motions: Flow<List<MotionSample>> get() = emptyFlow()

    fun isConnected(): Boolean

    /** 근접 알림(ProximityMessage, FR-196). null이면 끔(isActive 거짓). */
    fun publishProximity(alert: ProximityAlert?)

    /** 추월·역전 순간 1초 긴 진동(LeadChangeMessage, FR-188). [kind]는 Guidance.Kind.OVERTOOK 또는 OVERTAKEN이다. */
    fun publishLeadChange(kind: Guidance.Kind)

    /**
     * 짝지은 워치가 스스로 러닝을 측정 중인가(WATCH_MEASURING의 마지막 값, FR-252, D-373 (2)). 참이면 폰 측정·매칭을 시작하지 않는다.
     * 모르면 거짓이다("모르면 막지 않는다"). 폰 구현(data.watch.WatchLink)은 마지막 참이 오래되면 거짓으로 보고(D-390),
     * 워치 단독 측정의 구현(watch.run.LocalWatchGateway)은 자기 자신이라 늘 거짓이다(D-369).
     */
    fun isWatchMeasuring(): Boolean = false

    /** 워치 일시정지 요청의 동작(wear.contract PauseRequestMessage.Action과 같은 값). domain은 wear.contract를 모른다(5.1.2). */
    enum class PauseAction {
        PAUSE,
        RESUME
    }
}
