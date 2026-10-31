package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.watch.alert.ProximityAlertPlayer
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.ProximityMessage
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 워치 단독 측정에서 WatchGateway 포트를 워치 안에서 끝낸다(3.1, D-369). domain RunWatchBridge가 이 포트를 부르므로 근접 알림 규칙
 * (ProximityAlertRule, D-119)이 폰과 같이 돈다.
 *
 * - [publishProximity]: ProximityAlert → ProximityMessage 변환은 wear.contract의 of·off 한 곳(D-393, H-58)을 쓰고 워치 재생기에 바로 넘긴다.
 * - [publishLeadChange]: 추월·역전 1초 긴 진동. 재생기를 거쳐 반복 중인 비프를 긴 진동이 끝날 때까지 미룬다(폰에서 받을 때와 같다).
 * - [heartRates]: 운동 세션의 심박. 심박 동의와 워치연동이 켜져 있을 때만 내보낸다(FR-041).
 * - 화면이 RunTracker를 직접 부르므로 시작·종료·일시정지 요청은 빈 흐름이고, 표시는 버린다. 연결은 늘 참, 워치 측정 중은 늘 거짓(자기 자신).
 */
@Singleton
class LocalWatchGateway @Inject constructor(
    private val alertPlayer: ProximityAlertPlayer,
    private val settings: SettingsRepository,
    private val clock: Clock
) : WatchGateway {
    private val heart = MutableSharedFlow<List<HeartRateSample>>(extraBufferCapacity = HEART_BUFFER)

    override fun publish(d: WatchDisplay) = Unit

    override val startRequests: Flow<Unit> = emptyFlow()

    override val endRequests: Flow<Unit> = emptyFlow()

    override val pauseRequests: Flow<WatchGateway.PauseAction> = emptyFlow()

    override val heartRates: Flow<List<HeartRateSample>> = heart.asSharedFlow()

    override fun isConnected(): Boolean = true

    override fun publishProximity(alert: ProximityAlert?) {
        val now = clock.millis()
        val message = if (alert == null) {
            ProximityMessage.off(now)
        } else {
            ProximityMessage.of(alert.gapMeters, alert.interval.toMillis(), alert.isAhead, now)
        }
        alertPlayer.onProximity(message)
    }

    override fun publishLeadChange(kind: Guidance.Kind) {
        val leadKind = when (kind) {
            Guidance.Kind.OVERTOOK -> LeadChangeMessage.Kind.OVERTOOK
            Guidance.Kind.OVERTAKEN -> LeadChangeMessage.Kind.OVERTAKEN
            else -> return
        }
        alertPlayer.onLeadChange(LeadChangeMessage(leadKind, clock.millis()))
    }

    override fun isWatchMeasuring(): Boolean = false

    /** 운동 세션의 심박을 넘긴다(WatchMeasurementService가 부른다). 동의가 없으면 버린다. */
    fun offerHeartRates(samples: List<HeartRateSample>) {
        if (samples.isEmpty() || !settings.heartRateConsented() || !settings.watchLinked()) return
        heart.tryEmit(samples)
    }

    private companion object {
        const val HEART_BUFFER = 64
    }
}
