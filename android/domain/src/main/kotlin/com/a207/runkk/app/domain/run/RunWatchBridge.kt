package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 측정 흐름과 워치 사이의 메시지를 옮긴다(RunTracker 협력 객체, D-95). WatchGateway는 이 클래스만 부른다.
 * - 근접 알림: 마지막으로 보낸 값·시각을 갖고 ProximityAlertRule.shouldSend가 참일 때만 보낸다(D-119). 회마다 [resetProximity].
 */
class RunWatchBridge
@Inject
constructor(private val gateway: WatchGateway, private val scope: CoroutineScope) {
    private val lock = Any()
    private var lastSent: ProximityAlert? = null
    private var lastSentAt: Instant? = null

    /** 워치의 시작·종료·일시정지 요청과 심박을 [listener]로 넘긴다. RunTracker가 한 번 부른다. */
    fun bind(listener: WatchRequestListener) {
        scope.launch { gateway.startRequests.collect { deliver { listener.onStartRequested() } } }
        scope.launch { gateway.endRequests.collect { deliver { listener.onEndRequested() } } }
        scope.launch {
            gateway.pauseRequests.collect { action ->
                deliver {
                    when (action) {
                        WatchGateway.PauseAction.PAUSE -> listener.onPauseRequested()
                        WatchGateway.PauseAction.RESUME -> listener.onResumeRequested()
                    }
                }
            }
        }
        scope.launch { gateway.heartRates.collect { deliver { listener.onHeartRates(it) } } }
        scope.launch { gateway.motions.collect { deliver { listener.onWatchMotions(it) } } }
    }

    /**
     * 요청 하나를 넘긴다. 처리 중 예외가 나도 구독은 이어 간다(한 요청의 실패로 워치 요청 경로가 프로세스 끝까지 끊기지 않게).
     * 삼킨 예외는 앱 범위의 CoroutineExceptionHandler로 넘겨 운영 로그에 남긴다(domain은 Timber를 쓰지 않는다, AN5-C-13).
     * 취소는 그대로 전한다.
     */
    private inline fun deliver(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            scope.coroutineContext[CoroutineExceptionHandler]?.handleException(scope.coroutineContext, e)
        }
    }

    /** 표시를 보낸다. */
    fun publish(display: WatchDisplay) {
        gateway.publish(display)
    }

    /** 워치 시작 화면(오늘 계획, 측정 아님). */
    fun publishHome(plan: PlanSnapshot?) {
        publish(
            WatchDisplay(
                isRunning = false,
                elapsed = Duration.ZERO,
                distance = Distance.ZERO,
                currentPace = null,
                heartRateBpm = null,
                todayPlan = plan,
                notice = null
            )
        )
    }

    /** 근접 알림을 보낼 때면 보낸다. null은 끔이다. [now]는 마지막 전송 뒤 지난 시간을 재는 시각이다(시각은 매개변수, 6.1.14). */
    fun publishProximity(alert: ProximityAlert?, now: Instant) {
        val shouldSend =
            synchronized(lock) {
                val since = lastSentAt?.let { Duration.between(it, now) }
                ProximityAlertRule.shouldSend(lastSent, alert, since).also { send ->
                    if (send) {
                        lastSent = alert
                        lastSentAt = now
                    }
                }
            }
        if (shouldSend) gateway.publishProximity(alert)
    }

    /** 마지막 전송 값·시각을 비운다(회마다, D-158 (3)). */
    fun resetProximity() {
        synchronized(lock) {
            lastSent = null
            lastSentAt = null
        }
    }

    fun isWatchConnected(): Boolean = gateway.isConnected()

    /** 짝지은 워치가 스스로 측정 중인가(D-373 (2)). */
    fun isWatchMeasuring(): Boolean = gateway.isWatchMeasuring()

    /** 추월(OVERTOOK)·역전(OVERTAKEN) 순간을 보낸다. 그 밖의 안내 종류는 보내지 않는다. */
    fun publishLeadChange(kind: Guidance.Kind) {
        if (kind == Guidance.Kind.OVERTOOK || kind == Guidance.Kind.OVERTAKEN) gateway.publishLeadChange(kind)
    }
}
