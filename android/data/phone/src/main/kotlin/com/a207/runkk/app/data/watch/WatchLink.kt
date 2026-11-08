package com.a207.runkk.app.data.watch

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.run.CadenceMeter
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.MotionSample
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchNotice
import com.a207.runkk.app.domain.run.WatchResendRequester
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.PauseRequestMessage
import com.a207.runkk.wear.contract.ProximityMessage
import com.a207.runkk.wear.contract.RejectedCountMessage
import com.a207.runkk.wear.contract.ResendRejectedMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WatchMeasuringMessage
import com.a207.runkk.wear.contract.WearPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
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
 *
 * W40·W43(WatchResendRequester): 설정의 "다시 보내기"가 부르면 워치에 거부 기록 다시 보내기를 DataClient 항목(RESEND_REJECTED)으로
 * 남긴다(워치가 지금 닿지 않아도 연결되면 전달된다). 워치가 알린 거부 기록 건수(REJECTED_COUNT)는 마지막 값을 쥐고, 워치가 연결되어
 * 있고 워치 연동이 켜져 있을 때만 낸다(아니면 0). 앱을 띄울 때와 워치가 다시 연결될 때 Data Layer에 남은 건수 항목을 읽는다.
 */
@Singleton
class WatchLink internal constructor(
    private val transport: Transport,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
    /** 안내 키 → 워치에 보낼 문구(M-3). 운영은 문자열 리소스, 시험은 키 이름이다. */
    private val noticeText: (WatchNotice) -> String = { it.name },
    private val clock: Clock = Clock.systemUTC()
) : WatchGateway,
    WatchResendRequester {
    /**
     * 운영 조립. 송수신(PlayServicesTransport)은 WatchSessionPublisher와 같은 객체를 나눠 쓴다. WatchLink는 앱 시작 때
     * (WatchHomeSync → RunTracker → RunWatchBridge) 만들어지므로 여기서 워치 로그인 정보 보내기를 시작한다(A2 결정).
     */
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        transport: PlayServicesTransport,
        settings: SettingsRepository,
        scope: CoroutineScope,
        clock: Clock,
        sessionPublisher: WatchSessionPublisher
    ) : this(transport, settings, scope, { context.getString(it.textRes()) }, clock) {
        sessionPublisher.start()
    }

    /** Data Layer 송수신. 운영은 Play 서비스, 시험은 가짜다. */
    internal interface Transport {
        val incoming: SharedFlow<Incoming>

        /** 연결된 워치 노드가 있는가. 거짓 → 참이 되면 WatchLink가 표시 정보를 다시 보낸다. */
        val connected: StateFlow<Boolean>

        fun send(path: String, payload: ByteArray)

        /**
         * 줄을 서지 않고 바로 보내고 끝날 때까지 기다린다(로그인 정보 WATCH_SESSION, MessageClient만, D-371). [nodeId]가 있으면 그 워치에만,
         * 없으면 닿는 모든 워치에 보낸다. 한 곳에라도 보냈으면 참이다.
         */
        suspend fun sendNow(path: String, payload: ByteArray, nodeId: String?): Boolean

        /** DataClient 항목을 넣는다(로그아웃 표시 SESSION_REVOKED, 비밀값 없음). 연결이 끊긴 동안에도 남았다가 전달된다. 넣었으면 참. */
        suspend fun putItem(path: String, payload: ByteArray): Boolean

        /** 이 경로의 DataClient 항목을 지운다(새 로그인 정보를 보낸 뒤 옛 로그아웃 표시, D-390). 지웠으면 참. */
        suspend fun deleteItems(path: String): Boolean

        /** 이 경로의 DataClient 항목 내용(노드마다 하나, 워치 거부 기록 건수 W43). 없거나 읽지 못하면 빈 목록. */
        suspend fun readItems(path: String): List<ByteArray>

        /**
         * 이 워치 노드가 닿는다(워치가 보낸 메시지, 수신 서비스가 받은 표시 요청). 노드 목록에 없으면 더해 이후 메시지를 버리지 않는다
         * (S15P31A207-278).
         */
        fun markReachable(nodeId: String)
    }

    /** 워치에서 온 메시지·데이터 항목 하나(경로, 내용, 보낸 워치 노드). */
    internal class Incoming(val path: String, val payload: ByteArray, val sourceNodeId: String? = null)

    private val lock = Any()
    private var lastDisplay: WatchDisplay? = null

    /** 마지막으로 받은 워치 측정 중 여부와 받은 시각(폰 시계). lock 안에서만 쓴다(D-373 (2), D-390). */
    private var lastWatchMeasuring: WatchMeasuringMessage? = null
    private var lastMeasuringAt: Instant? = null

    /** 워치가 마지막으로 알린 거부 기록 건수(W43). 연결·연동 여부와 상관없이 마지막 값을 쥔다. */
    private val watchRejected = MutableStateFlow<RejectedCountMessage?>(null)

    /** 이미 넘긴 심박의 측정 시각(밀리초). 워치가 같은 묶음을 다시 넣어도(DataItem 재동기화) 한 번만 넘긴다. lock 안에서만 쓴다. */
    private val seenHeartRates = LinkedHashSet<Long>()

    /** 이미 넘긴 손목 걸음의 측정 시각(밀리초). 심박과 같은 이유로 한 번만 넘긴다. lock 안에서만 쓴다. */
    private val seenSteps = LinkedHashSet<Long>()

    /** 누적 걸음 수(STEPS만 지원하는 워치)를 케이던스로 바꾼다. 누적 값이 줄면(새 운동 세션) 처음부터 센다. lock 안에서만 쓴다. */
    private val wristCadence = CadenceMeter()

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

    /**
     * 동행 워치의 손목 케이던스(S15P31A207-242). 심박 묶음의 steps에서 꺼내고 심박과 같은 허용 규칙을 따른다. 분당 걸음 수는 그대로,
     * 누적 걸음 수는 CadenceMeter로 바꾼다. 옛 워치의 묶음은 steps가 없어 아무것도 내지 않는다.
     */
    override val motions: Flow<List<MotionSample>> =
        transport.incoming
            .filter { it.path.startsWith(WearPaths.HEART_RATE_BATCH) }
            .mapNotNull { decodeOrNull { HeartRateBatchMessage.decode(it.payload) } }
            .filter { it.steps.isNotEmpty() && collectionAllowed() }
            .map { batch -> wristCadences(batch.steps) }
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
        scope.launch {
            transport.incoming
                .filter { it.path == WearPaths.WATCH_MEASURING }
                .mapNotNull { decodeOrNull { WatchMeasuringMessage.decode(it.payload) } }
                .collect(::onWatchMeasuring)
        }
        scope.launch {
            transport.incoming
                .filter { it.path == WearPaths.REJECTED_COUNT }
                .mapNotNull { decodeOrNull { RejectedCountMessage.decode(it.payload) } }
                .collect(::onWatchRejectedCount)
        }
        // 앱을 띄울 때(이미 연결됨)와 워치가 다시 연결될 때 Data Layer에 남은 워치 거부 기록 건수를 읽는다(W43).
        scope.launch {
            transport.connected
                .filter { it }
                .collect { readWatchRejectedCount() }
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

    /**
     * 워치가 지금 표시를 요청했다(DISPLAY_REQUEST, S15P31A207-278). 워치 앱이 꺼져 있던 동안 보낸 일회성 표시를 잃었으므로
     * 요청한 워치를 닿는 노드로 등록하고, 마지막 표시(측정 중 진행값·대기 화면 오늘 계획·종료 표시, 없으면 대기 화면)를 지금 허용 여부로
     * 다시 보낸다. 폰 프로세스가 막 깨어났으면 측정 중이 아니므로 대기 화면이다. 요청이 몰려도 보낼 표시는 송신 줄에서 최신 1건만 남는다.
     * 매니페스트 수신 서비스(PhoneWatchListenerService)만 부른다: 앱 안 리스너로 온 같은 요청은 받지 않는다(두 번 보내지 않게).
     */
    internal fun onDisplayRequested(nodeId: String) {
        transport.markReachable(nodeId)
        resend(collectionAllowed())
    }

    /**
     * 짝지은 워치가 스스로 측정 중인가(FR-252, D-373 (2), 3.6). 마지막으로 받은 WATCH_MEASURING이 참이고, 받은 지 [MEASURING_TTL] 안이고,
     * 워치가 닿을 때만 참이다. 워치 앱이 강제 종료돼 거짓이 오지 않아도 폰 측정이 막힌 채 남지 않는다(D-390, H-57). 시각은 워치 시계가
     * 아니라 폰이 받은 시각([clock])으로 잰다(두 기기 시계 차이와 무관하게).
     */
    override fun isWatchMeasuring(): Boolean {
        val (last, at) = synchronized(lock) { lastWatchMeasuring to lastMeasuringAt }
        if (last?.isMeasuring != true || at == null || !transport.connected.value) return false
        return Duration.between(at, clock.instant()) <= MEASURING_TTL
    }

    /** 워치 측정 중 여부를 받았다. 늦게 도착한 옛 메시지(보낸 시각이 마지막 값보다 이름)는 버린다. */
    private fun onWatchMeasuring(message: WatchMeasuringMessage) = synchronized(lock) {
        val last = lastWatchMeasuring
        if (last != null && message.atEpochMillis < last.atEpochMillis) return@synchronized
        lastWatchMeasuring = message
        lastMeasuringAt = clock.instant()
    }

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

    /**
     * 워치에 거부 기록 다시 보내기를 요청한다(W40). DataClient 항목이라 워치가 지금 닿지 않아도 남았다가 연결되면 전달된다(W43).
     * 요청 시각이 매번 달라 같은 경로에 넣어도 바뀐 항목으로 간다. 항목을 넣지 못하면(Play 서비스 없음) 기록만 남긴다.
     */
    override suspend fun requestResend() {
        if (!transport.putItem(WearPaths.RESEND_REJECTED, ResendRejectedMessage(clock.millis()).encode())) {
            Timber.w("워치 다시 보내기 요청을 남기지 못했다")
        }
    }

    /** 워치 거부 기록 건수(W43). 워치가 연결되지 않았거나 워치 연동이 꺼져 있으면 0이다. */
    override fun observeWatchRejectedCount(): Flow<Int> = combine(
        watchRejected,
        transport.connected,
        settings.changes().map { it.isWatchLinked }.onStart { emit(settings.watchLinked()) }
    ) { last, isConnected, isLinked -> if (isConnected && isLinked) last?.count ?: 0 else 0 }
        .distinctUntilChanged()

    /** 받은 워치 거부 기록 건수. 늦게 도착한 옛 값(센 시각이 마지막 값보다 이름)은 버린다. */
    private fun onWatchRejectedCount(message: RejectedCountMessage) {
        watchRejected.update { last -> last?.takeIf { it.atEpochMillis > message.atEpochMillis } ?: message }
    }

    private suspend fun readWatchRejectedCount() {
        transport
            .readItems(WearPaths.REJECTED_COUNT)
            .mapNotNull { bytes -> decodeOrNull { RejectedCountMessage.decode(bytes) } }
            .maxByOrNull { it.atEpochMillis }
            ?.let(::onWatchRejectedCount)
    }

    /** 처음 보는 측정 시각의 표본만 도메인 심박으로 바꾼다. 기억은 최근 SEEN_LIMIT개까지. */
    private fun firstSeen(samples: List<HeartRateBatchMessage.Sample>): List<HeartRateSample> = synchronized(lock) {
        val fresh = samples.filter { seenHeartRates.add(it.measuredAtEpochMillis) }
        while (seenHeartRates.size > SEEN_LIMIT) seenHeartRates.remove(seenHeartRates.first())
        fresh.map { it.toDomain() }
    }

    /** 처음 보는 측정 시각의 걸음만 케이던스 표본으로 바꾼다. 기억은 최근 SEEN_LIMIT개까지. */
    private fun wristCadences(steps: List<HeartRateBatchMessage.Step>): List<MotionSample> = synchronized(lock) {
        val fresh = steps.sortedBy { it.measuredAtEpochMillis }.filter { seenSteps.add(it.measuredAtEpochMillis) }
        while (seenSteps.size > SEEN_LIMIT) seenSteps.remove(seenSteps.first())
        fresh.mapNotNull { step ->
            val at = Instant.ofEpochMilli(step.measuredAtEpochMillis)
            val cadence = step.perMinute?.toDouble() ?: step.total?.let { wristCadence.accept(at, it) }
            cadence?.let { MotionSample(at, cadenceSpm = it) }
        }
    }

    private fun collectionAllowed(): Boolean = settings.heartRateConsented() && settings.watchLinked()

    /** 허용 여부가 바뀌었거나 워치가 요청했다: 마지막 표시 정보(없으면 대기 화면)를 [allowed]로 다시 보낸다. */
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

        /** 워치 측정 중(참)을 믿는 시간 (제안) 90초. 워치는 측정 중 30초마다 다시 보낸다(D-390, R69 H-57). */
        val MEASURING_TTL: Duration = Duration.ofSeconds(90)
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

/** 근접 알림 → 워치 메시지. null은 끔이다(isActive 거짓, 격차·간격 0, F.4.2). 변환 규칙은 wear.contract 한 곳에 있다(D-393, H-58). */
internal fun ProximityAlert?.toMessage(sentAtEpochMillis: Long): ProximityMessage =
    this?.let { ProximityMessage.of(it.gapMeters, it.interval.toMillis(), it.isAhead, sentAtEpochMillis) }
        ?: ProximityMessage.off(sentAtEpochMillis)

internal fun PauseRequestMessage.Action.toDomain(): WatchGateway.PauseAction = when (this) {
    PauseRequestMessage.Action.PAUSE -> WatchGateway.PauseAction.PAUSE
    PauseRequestMessage.Action.RESUME -> WatchGateway.PauseAction.RESUME
}

@StringRes
internal fun WatchNotice.textRes(): Int = when (this) {
    WatchNotice.ALREADY_MEASURING -> R.string.watch_notice_already_measuring
    WatchNotice.OPEN_RUN_SCREEN -> R.string.watch_notice_open_run_screen
    WatchNotice.END_ON_PHONE -> R.string.watch_notice_end_on_phone
}

internal fun HeartRateBatchMessage.Sample.toDomain(): HeartRateSample =
    HeartRateSample(Instant.ofEpochMilli(measuredAtEpochMillis), bpm)
