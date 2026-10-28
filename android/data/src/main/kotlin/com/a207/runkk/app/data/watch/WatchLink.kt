package com.a207.runkk.app.data.watch

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchNotice
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.PauseRequestMessage
import com.a207.runkk.wear.contract.ProximityMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 워치와 Wearable Data Layer로 통신한다(6.1.14 data.watch, D-20). WatchGateway 구현.
 * 시작 요청·표시 정보는 MessageClient, 심박 묶음은 DataClient(끊겨도 쌓였다가 전달, NFR-032)다. 메시지 형식은 wear.contract.
 *
 * 심박 수집 허용 = SettingsRepository.heartRateConsented() && watchLinked()(NFR-023, H-19).
 * - 보내는 표시 정보마다 허용 여부를 싣고, 설정 사본이 바뀌면(changes) 마지막 표시 정보를 바뀐 허용 여부로 바로 다시 보낸다.
 * - 허용되지 않은 동안 온 심박은 버린다. 측정 실패(FAILED) 표본도 버린다(FR-041).
 *
 * v0.11(F.4, FR-194, FR-196): 워치의 일시정지 요청(PAUSE_REQUEST)을 pauseRequests로 넘기고(모르는 action은 버리고 경고 로그, D-157 (6)),
 * 근접 알림(PROXIMITY)·추월·역전(LEAD_CHANGE)을 보낸다. 보낼지·언제 보낼지는 RunWatchBridge가 정하고 여기서는 메시지로 바꿔 보내기만 한다.
 * 보낸 시각은 [clock]의 지금 시각이다.
 */
@Singleton
class WatchLink internal constructor(
    private val transport: Transport,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
    /** 안내 키 → 워치에 보낼 문구(M-3). 운영은 문자열 리소스, 시험은 키 이름이다. */
    private val noticeText: (WatchNotice) -> String = { it.name },
    private val clock: Clock = Clock.systemUTC()
) : WatchGateway {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        scope: CoroutineScope,
        clock: Clock
    ) : this(PlayServicesTransport(context, scope), settings, scope, { context.getString(it.textRes()) }, clock)

    /** Data Layer 송수신. 운영은 Play 서비스, 시험은 가짜다. */
    internal interface Transport {
        val incoming: SharedFlow<Incoming>

        /** 연결된 워치 노드가 있는가. 거짓 → 참이 되면 WatchLink가 표시 정보를 다시 보낸다. */
        val connected: StateFlow<Boolean>

        fun send(path: String, payload: ByteArray)
    }

    /** 워치에서 온 메시지·데이터 항목 하나(경로, 내용). */
    internal class Incoming(val path: String, val payload: ByteArray)

    private val lock = Any()
    private var lastDisplay: WatchDisplay? = null

    /** 이미 넘긴 심박의 측정 시각(밀리초). 워치가 같은 묶음을 다시 넣어도(DataItem 재동기화) 한 번만 넘긴다. lock 안에서만 쓴다. */
    private val seenHeartRates = LinkedHashSet<Long>()

    override val startRequests: Flow<Unit> =
        transport.incoming
            .filter { it.path == WearPaths.START_REQUEST }
            .mapNotNull { decodeOrNull { StartRequestMessage.decode(it.payload) } }
            .map { }

    override val heartRates: Flow<List<HeartRateSample>> =
        transport.incoming
            .filter { it.path.startsWith(WearPaths.HEART_RATE_BATCH) }
            .mapNotNull { decodeOrNull { HeartRateBatchMessage.decode(it.payload) } }
            .filter { collectionAllowed() }
            .map { batch -> firstSeen(batch.samples.filter { it.status == HeartRateBatchMessage.Status.OK }) }
            .filter { it.isNotEmpty() }

    override val endRequests: Flow<Unit> =
        transport.incoming
            .filter { it.path == WearPaths.END_REQUEST }
            .mapNotNull { decodeOrNull { EndRequestMessage.decode(it.payload) } }
            .map { }

    init {
        scope.launch {
            settings
                .changes()
                .map { it.isHeartRateConsented && it.isWatchLinked }
                .distinctUntilChanged()
                .collect { allowed -> resend(allowed) }
        }
        // 워치가 (다시) 연결되면 시작 화면·진행 표시와 수집 허용 여부를 바로 맞춘다.
        scope.launch {
            transport.connected
                .drop(1)
                .filter { it }
                .collect { resend(collectionAllowed()) }
        }
    }

    override fun publish(d: WatchDisplay) {
        synchronized(lock) { lastDisplay = d }
        transport.send(WearPaths.WATCH_DISPLAY, d.toMessage(collectionAllowed(), noticeText).encode())
    }

    override fun isConnected(): Boolean = transport.connected.value

    /** 워치 일시정지·재개 요청(FR-194). 모르는 action(해독 실패)은 버리고 경고 로그를 남긴다(D-157 (6)). 시각은 폰이 처리 때 정한다. */
    override val pauseRequests: Flow<WatchGateway.PauseAction> =
        transport.incoming
            .filter { it.path == WearPaths.PAUSE_REQUEST }
            .mapNotNull { incoming ->
                val request = decodeOrNull { PauseRequestMessage.decode(incoming.payload) }
                if (request == null) Timber.w("해독할 수 없는 워치 일시정지 요청을 버린다")
                request?.action?.toDomain()
            }

    /** 근접 알림(FR-196, D-96). null이면 끔(isActive 거짓, 격차·간격 0). */
    override fun publishProximity(alert: ProximityAlert?) {
        transport.send(WearPaths.PROXIMITY, alert.toMessage(clock.millis()).encode())
    }

    /** 추월·역전 1초 긴 진동(FR-188, FR-196). OVERTOOK·OVERTAKEN이 아닌 키는 보내지 않는다. */
    override fun publishLeadChange(kind: Guidance.Kind) {
        val lead =
            when (kind) {
                Guidance.Kind.OVERTOOK -> LeadChangeMessage.Kind.OVERTOOK
                Guidance.Kind.OVERTAKEN -> LeadChangeMessage.Kind.OVERTAKEN
                else -> return
            }
        transport.send(WearPaths.LEAD_CHANGE, LeadChangeMessage(lead, clock.millis()).encode())
    }

    /** 처음 보는 측정 시각의 표본만 도메인 심박으로 바꾼다. 기억은 최근 SEEN_LIMIT개까지. */
    private fun firstSeen(samples: List<HeartRateBatchMessage.Sample>): List<HeartRateSample> = synchronized(lock) {
        val fresh = samples.filter { seenHeartRates.add(it.measuredAtEpochMillis) }
        while (seenHeartRates.size > SEEN_LIMIT) seenHeartRates.remove(seenHeartRates.first())
        fresh.map { it.toDomain() }
    }

    private fun collectionAllowed(): Boolean = settings.heartRateConsented() && settings.watchLinked()

    /** 허용 여부가 바뀌었다: 마지막 표시 정보(없으면 대기 화면)를 새 허용 여부로 다시 보낸다. */
    private fun resend(allowed: Boolean) {
        val message =
            synchronized(lock) { lastDisplay }?.toMessage(allowed, noticeText)
                ?: WatchDisplayMessage(
                    isRunning = false,
                    elapsedSeconds = 0,
                    distanceMeters = 0,
                    isHeartRateCollectionAllowed = allowed
                )
        transport.send(WearPaths.WATCH_DISPLAY, message.encode())
    }

    private inline fun <T> decodeOrNull(decode: () -> T): T? = try {
        decode()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        /** 심박 1초 간격으로 1시간 남짓. */
        const val SEEN_LIMIT = 4_096
    }
}

// wear.contract 메시지 ↔ 도메인 매퍼(6.1.14 data.watch).

internal fun WatchDisplay.toMessage(
    heartRateCollectionAllowed: Boolean,
    noticeText: (WatchNotice) -> String
): WatchDisplayMessage = WatchDisplayMessage(
    isRunning = isRunning,
    elapsedSeconds = elapsed.seconds,
    distanceMeters = distance.meters,
    currentPaceSecondsPerKm = currentPace?.secondsPerKm,
    heartRateBpm = heartRateBpm,
    planDistanceMeters = todayPlan?.targetDistance?.meters,
    planPaceSecondsPerKm = todayPlan?.targetPace?.secondsPerKm,
    isHeartRateCollectionAllowed = heartRateCollectionAllowed,
    notice = notice?.let(noticeText),
    isPaused = isPaused,
    isPausable = isPausable
)

/** 근접 알림 → 워치 메시지. null은 끔이다(isActive 거짓, 격차·간격 0, F.4.2). */
internal fun ProximityAlert?.toMessage(sentAtEpochMillis: Long): ProximityMessage = ProximityMessage(
    isActive = this != null,
    gapMeters = this?.gapMeters ?: 0,
    intervalMillis = this?.interval?.toMillis() ?: 0,
    isAhead = this?.isAhead ?: false,
    sentAtEpochMillis = sentAtEpochMillis
)

internal fun PauseRequestMessage.Action.toDomain(): WatchGateway.PauseAction = when (this) {
    PauseRequestMessage.Action.PAUSE -> WatchGateway.PauseAction.PAUSE
    PauseRequestMessage.Action.RESUME -> WatchGateway.PauseAction.RESUME
}

@StringRes
internal fun WatchNotice.textRes(): Int = when (this) {
    WatchNotice.ALREADY_MEASURING -> R.string.watch_notice_already_measuring
    WatchNotice.OPEN_RUN_SCREEN -> R.string.watch_notice_open_run_screen
    WatchNotice.END_ON_PHONE -> R.string.watch_notice_end_on_phone
    WatchNotice.AUTO_END_SOON -> R.string.watch_notice_auto_end_soon
    WatchNotice.AUTO_ENDED -> R.string.watch_notice_auto_ended
}

internal fun HeartRateBatchMessage.Sample.toDomain(): HeartRateSample =
    HeartRateSample(Instant.ofEpochMilli(measuredAtEpochMillis), bpm)
