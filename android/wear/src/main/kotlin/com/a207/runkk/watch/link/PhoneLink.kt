package com.a207.runkk.watch.link

import com.a207.runkk.watch.alert.ProximityAlertPlayer
import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.heart.WatchHeartRate
import com.a207.runkk.watch.heart.WatchSteps
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.PauseRequestMessage
import com.a207.runkk.wear.contract.ProximityMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WatchMeasuringMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 폰과 메시지를 주고받는다(6.1.15, D-20). 메시지 형식은 wear.contract만 쓴다.
 *
 * - 폰 → 워치: 표시 정보(MessageClient, [WearPaths.WATCH_DISPLAY])를 [displays]로 내보내고,
 *   그 허용 플래그·러닝 여부로 [HeartRateCollector]를 켜고 끈다. 허용이 거짓이면 즉시 멈추고 보내지 않은 심박을 버린다(NFR-023).
 * - 폰 → 워치: 근접 알림([WearPaths.PROXIMITY])·추월 역전([WearPaths.LEAD_CHANGE])은 [ProximityAlertPlayer]에 넘긴다(FR-196, FR-188).
 *   근접 알림은 [proximity]로, 추월·역전의 방향과 받은 시각은 [leadChange]로도 내보내 측정 화면이 거리 차와 "역전!"을 보인다.
 *   마지막 표시가 러닝 중일 때만 넘기고, 러닝이 끝났다는 표시를 받으면 재생을 멈춘다. 모르는 경로는 버린다(F.4.4).
 * - 워치 → 폰: 지금 표시 요청([requestDisplay], S15P31A207-278). 앱 시작과 화면 재개 때 보내 꺼져 있던 동안 잃은 표시를 되찾는다.
 * - 워치 → 폰: 로그인 정보 요청([requestSession], FR-242)과 워치 측정 중 여부([sendMeasuring], FR-252, D-371). 로그인 정보·로그아웃
 *   표시는 토큰을 이 클래스에 두지 않도록 PhoneMessageService가 watch.session으로 바로 넘긴다.
 * - 워치 → 폰: 시작 요청(MessageClient, [WearPaths.START_REQUEST], FR-183), 종료 요청(MessageClient,
 *   [WearPaths.END_REQUEST], FR-181), 일시정지·재개 요청(MessageClient, [WearPaths.PAUSE_REQUEST], FR-194)과
 *   심박 묶음(DataClient, [WearPaths.HEART_RATE_BATCH] 아래 묶음마다 다른 경로, FR-043, NFR-032). 같은 운동 세션이 잰 손목 걸음도
 *   묶음의 steps로 함께 보낸다(S15P31A207-242). 심박과 같은 허용 규칙을 따른다(심박 동의가 없으면 세션이 열리지 않아 걸음도 없다).
 *
 * 수신(메시지·심박)은 상태만 바꾸고 바로 돌아오며, 전송은 따로 도는 코루틴이 맡는다.
 * 그래서 Data Layer가 늦어져도 철회 메시지 처리와 심박 수신이 막히지 않는다.
 * 워치는 기록을 만들지 않는다(C-002). 심박은 폰에 넘길 때까지 메모리에만 잠깐 둔다.
 */
@Singleton
class PhoneLink @Inject constructor(
    private val dataLayer: WearDataLayer,
    private val collector: HeartRateCollector,
    private val alertPlayer: ProximityAlertPlayer,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    /** 아래 가변 상태(허용 여부·허용 세대·보내지 않은 심박·감시 작업)는 모두 이 잠금 안에서만 읽고 쓴다. */
    private val lock = Any()
    private var started = false
    private var collectionAllowed = false

    /** 허용이 거짓 → 참으로 바뀐 시각. 그 전에 잰 심박(철회 전 값)은 다시 허용되어도 보내지 않는다. */
    private var allowedSinceMillis = Long.MAX_VALUE

    /** 철회될 때마다 늘린다. 꺼낸 묶음을 보내기 직전에 같은 세대인지 확인한다. */
    private var allowGeneration = 0L
    private var batchSequence = 0L
    private var silenceWatchdog: Job? = null

    /** 표시 요청. 폰에 닿은 요청 뒤 [DISPLAY_REQUEST_GAP_MILLIS] 동안 살아 있어 그 사이 요청을 건너뛴다. */
    private var displayRequestJob: Job? = null

    /** 마지막으로 받은 표시 정보. 일시정지 요청을 보낼지(isPausable), 근접 알림을 재생할지(isRunning) 정한다. */
    private var lastDisplay: WatchDisplayMessage? = null

    /** 아직 폰에 넘기지 않은 심박(측정 순서). */
    private val pending = ArrayDeque<HeartRateBatchMessage.Sample>()

    /** 아직 폰에 넘기지 않은 손목 걸음(측정 순서). */
    private val pendingSteps = ArrayDeque<HeartRateBatchMessage.Step>()

    /** 전송 요청 신호. 여러 번 와도 한 번 보내면 충분하다. */
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)

    private val _displays = MutableSharedFlow<WatchDisplayMessage>(
        replay = 1,
        extraBufferCapacity = DISPLAY_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 폰이 보낸 표시 정보(진행 지표·오늘 계획·허용 플래그·안내). 마지막 한 건을 새 구독자에게 다시 준다. */
    val displays: Flow<WatchDisplayMessage> = _displays.asSharedFlow()

    private val _lastDisplayAtMillis = MutableStateFlow<Long?>(null)

    /** 마지막 표시 정보를 받은 시각. 다시 만든 화면이 지난 표시를 새것으로 보지 않게 한다. */
    val lastDisplayAtMillis: StateFlow<Long?> = _lastDisplayAtMillis.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)

    /** 폰 노드가 연결되어 있는지(N-21 시작 화면의 폰 연결 상태). */
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private val _proximity = MutableStateFlow<PhoneProximity?>(null)

    /**
     * 러닝 중 받은 마지막 근접 알림(대결 근접 알림 화면, FR-196). 꺼짐 메시지나 러닝이 끝났다는 표시를 받으면 null.
     * 새 메시지가 끊긴 뒤의 만료(3초, D-158)는 화면 규칙이라 watch.ui가 받은 시각으로 판단한다.
     */
    val proximity: StateFlow<PhoneProximity?> = _proximity.asStateFlow()

    private val _leadChange = MutableStateFlow<PhoneLeadChange?>(null)

    /** 러닝 중 마지막으로 받은 추월·역전의 방향과 받은 시각(FR-188). 러닝이 끝났다는 표시를 받으면 null. 띠를 얼마나 둘지는 watch.ui가 정한다. */
    val leadChange: StateFlow<PhoneLeadChange?> = _leadChange.asStateFlow()

    /** 폰이 수집을 허용했지만 워치 OS 심박 권한이 없다. */
    val heartRatePermissionNeeded: StateFlow<Boolean> = collector.state
        .map { it == HeartRateCollector.State.NEEDS_PERMISSION }
        .stateIn(scope, SharingStarted.Eagerly, collector.state.value == HeartRateCollector.State.NEEDS_PERMISSION)

    /**
     * 지금 심박 센서를 켜고 모으는 중인지. 수집 중에만 포그라운드 서비스(watch.ui)를 띄운다.
     * 금지 플래그·러닝 끝·30분 침묵(MAX_SILENT_RUN_MILLIS)으로 수집이 멈추면 거짓이 되어 서비스도 내려간다.
     */
    val heartRateCollecting: StateFlow<Boolean> = collector.state
        .map { it == HeartRateCollector.State.COLLECTING }
        .stateIn(scope, SharingStarted.Eagerly, collector.state.value == HeartRateCollector.State.COLLECTING)

    private val _latestHeartRate = MutableStateFlow<HeartRateBatchMessage.Sample?>(null)

    /** 워치가 마지막으로 잰 정상 심박(화면 표시용). 수집 금지가 오면 지운다. */
    val latestHeartRate: StateFlow<HeartRateBatchMessage.Sample?> = _latestHeartRate.asStateFlow()

    /**
     * 수신·수집·전송·연결 확인을 시작한다. 앱 프로세스가 시작될 때 한 번 부르고, 거듭 불러도 한 번만 돈다.
     * 먼저 이전 프로세스가 남긴 운동 세션을 끝낸다: 새 프로세스는 폰의 허용을 아직 받지 않았다(NFR-023).
     * 새 프로세스는 표시 정보도 잃었으므로 폰에 지금 표시를 요청한다([requestDisplay], S15P31A207-278).
     */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        collector.endLeftoverSession()
        scope.launch { dataLayer.messages.collect { onMessage(it) } }
        scope.launch { collector.samples.collect { onSample(it) } }
        scope.launch { collector.steps.collect { onSteps(it) } }
        scope.launch { for (request in flushRequests) flush() }
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                flushRequests.trySend(Unit)
            }
        }
        scope.launch {
            while (isActive) {
                refreshConnection()
                delay(CONNECTION_POLL_MILLIS)
            }
        }
        requestDisplay()
    }

    /**
     * 폰에 지금 표시 정보를 요청한다(DISPLAY_REQUEST, 본문 없음, S15P31A207-278). 앱이 시작될 때와 화면이 다시 보일 때 부른다.
     * 폰→워치 표시는 일회성 메시지라 앱이 꺼져 있던 동안 온 것(측정 중 진행값·대기 화면 오늘 계획·종료 표시)은 남지 않는다. 폰은 마지막
     * 표시를 다시 보내고, 그 표시가 [displays]로 나가 동행 표시가 뜬다. 앱 시작과 첫 화면 재개가 함께 와도 한 번만 보내도록 폰에 닿은
     * 요청 뒤 [DISPLAY_REQUEST_GAP_MILLIS] 동안은 다시 보내지 않는다. 닿지 못한 요청(폰 노드 없음)은 간격을 두지 않는다(requestSession과 같다).
     */
    fun requestDisplay() {
        synchronized(lock) {
            if (displayRequestJob?.isActive == true) return
            displayRequestJob = scope.launch {
                if (sendToPhone(WearPaths.DISPLAY_REQUEST, ByteArray(0))) delay(DISPLAY_REQUEST_GAP_MILLIS)
            }
        }
    }

    /** 자유런 시작 요청(FR-183). 폰과 연결되어 있지 않거나 보내지 못하면 거짓("폰과 연결 필요"). */
    suspend fun sendStartRequest(): Boolean =
        sendToPhone(WearPaths.START_REQUEST, StartRequestMessage(requestedAtEpochMillis = clock.millis()).encode())

    /**
     * 종료 요청(워치에서 종료 버튼을 길게 누름, FR-181). 폰과 연결되어 있지 않거나 보내지 못하면 거짓.
     * 받은 폰은 자유런만 끝내고, 대결·크루런이면 "폰에서 끝내 주세요" 안내를 표시 정보로 돌려보낸다.
     */
    suspend fun sendEndRequest(): Boolean =
        sendToPhone(WearPaths.END_REQUEST, EndRequestMessage(requestedAtEpochMillis = clock.millis()).encode())

    /**
     * 일시정지·재개 요청(FR-194). 마지막으로 받은 표시가 러닝 중이고 isPausable이 참일 때만 보낸다(자유런 측정 중, F.4.2).
     * 표시를 받지 않았거나(옛 폰 앱 포함) 일시정지할 수 없으면, 또는 폰과 연결되어 있지 않거나 보내지 못하면 거짓.
     * 받은 폰이 상태를 바꾸고 다음 표시(isPaused)로 알린다. 워치는 요청만 보낸다(D-93).
     */
    suspend fun sendPauseRequest(action: PauseRequestMessage.Action): Boolean {
        val isPausable = synchronized(lock) { lastDisplay?.let { it.isRunning && it.isPausable } == true }
        if (!isPausable) return false
        val message = PauseRequestMessage(action = action, requestedAtEpochMillis = clock.millis())
        return sendToPhone(WearPaths.PAUSE_REQUEST, message.encode())
    }

    /** 일시정지 요청. 화면(watch.ui)이 wear.contract 형식을 몰라도 되게 둔 [sendPauseRequest]의 짧은 이름. */
    suspend fun requestPause(): Boolean = sendPauseRequest(PauseRequestMessage.Action.PAUSE)

    /** 재개 요청. [requestPause]와 같다. */
    suspend fun requestResume(): Boolean = sendPauseRequest(PauseRequestMessage.Action.RESUME)

    /**
     * 로그인 정보 요청(SESSION_REQUEST, 본문 없음, FR-242, D-371). 워치가 로그인 정보 없이 열렸을 때 화면이 부른다. 폰은 같은 노드로
     * WATCH_SESSION을 보내고, 그 메시지는 PhoneMessageService가 watch.session으로 넘긴다. 연결되어 있지 않거나 보내지 못하면 거짓.
     */
    suspend fun requestSession(): Boolean = sendToPhone(WearPaths.SESSION_REQUEST, ByteArray(0))

    /**
     * 워치 측정 중 여부(WATCH_MEASURING, FR-252, D-373). WatchMeasuringReporter가 바뀔 때와 측정 중 30초마다 부른다.
     * 연결되어 있지 않거나 보내지 못하면 거짓(다음 주기에 다시 보낸다).
     */
    suspend fun sendMeasuring(isMeasuring: Boolean): Boolean = sendToPhone(
        WearPaths.WATCH_MEASURING,
        WatchMeasuringMessage(isMeasuring = isMeasuring, atEpochMillis = clock.millis()).encode()
    )

    /** 폰 연결을 지금 다시 확인한다("다시 연결", 와이어프레임 K3). 연결된 폰 노드가 있으면 참. */
    suspend fun recheckConnection(): Boolean = refreshConnection().isNotEmpty()

    private suspend fun sendToPhone(path: String, bytes: ByteArray): Boolean {
        val nodes = refreshConnection()
        if (nodes.isEmpty()) {
            Timber.w("폰 노드가 없어 %s 를 보내지 못했다", path)
            return false
        }
        var delivered = false
        nodes.forEach { node -> delivered = dataLayer.sendMessage(node, path, bytes) || delivered }
        if (!delivered) Timber.w("폰에 %s 를 보내지 못했다", path)
        return delivered
    }

    /**
     * 심박 묶음을 DataClient로 넘긴다. 묶음마다 다른 경로에 넣어, 끊긴 동안 쌓인 묶음이 서로 덮어쓰지 않고
     * 연결되면 모두 동기화된다(NFR-032). 기기에 저장했으면 참.
     */
    suspend fun sendHeartRates(batch: HeartRateBatchMessage): Boolean {
        val first = batch.samples.firstOrNull()?.measuredAtEpochMillis
            ?: batch.steps.firstOrNull()?.measuredAtEpochMillis
            ?: return true
        val sequence = synchronized(lock) { batchSequence++ }
        val path = "${WearPaths.HEART_RATE_BATCH}/$first-$sequence"
        return dataLayer.putData(path, batch.encode())
    }

    /** OS 심박 권한 창에 쓸 권한 이름(watch.ui가 watch.heart를 모르게 여기서 넘긴다). */
    val heartRatePermissionName: String get() = collector.permissionName

    /** OS 심박 권한 결과가 바뀌었다(권한 창 닫힘, 화면 복귀). */
    fun onHeartRatePermissionChanged() = collector.onPermissionChanged()

    /** 경로로 나눈다. 모르는 경로(새 폰이 더한 경로 포함)와 해독할 수 없는 본문은 버린다(F.4.4). */
    private fun onMessage(message: WearMessage) {
        when (message.path) {
            WearPaths.WATCH_DISPLAY -> decodeOrNull(message) { WatchDisplayMessage.decode(it) }?.let(::onDisplay)

            WearPaths.PROXIMITY ->
                decodeOrNull(message) { ProximityMessage.decode(it) }
                    ?.takeIf { isRunning() }
                    ?.let(::onProximity)

            WearPaths.LEAD_CHANGE ->
                decodeOrNull(message) { LeadChangeMessage.decode(it) }
                    ?.takeIf { isRunning() }
                    ?.let(::onLeadChange)

            else -> Unit
        }
    }

    private fun onDisplay(display: WatchDisplayMessage) {
        synchronized(lock) { lastDisplay = display }
        _phoneConnected.value = true
        applyCollectionRules(display)
        _lastDisplayAtMillis.value = clock.millis()
        _displays.tryEmit(display)
        if (!display.isRunning) {
            alertPlayer.stop()
            _proximity.value = null
            _leadChange.value = null
            flushRequests.trySend(Unit)
        }
    }

    /** 근접 알림은 소리·진동으로 재생하고, 화면에도 보이게 마지막 값을 남긴다. */
    private fun onProximity(message: ProximityMessage) {
        alertPlayer.onProximity(message)
        _proximity.value = message.takeIf { it.isActive }?.let {
            PhoneProximity(
                gapMeters = it.gapMeters,
                isAhead = it.isAhead,
                intervalMillis = it.intervalMillis,
                receivedAtMillis = clock.millis()
            )
        }
    }

    /** 추월·역전은 긴 진동으로 재생하고, 화면에도 "역전!"을 띄우게 방향과 받은 시각을 남긴다. */
    private fun onLeadChange(message: LeadChangeMessage) {
        alertPlayer.onLeadChange(message)
        _leadChange.value = PhoneLeadChange(
            isOvertook = message.kind == LeadChangeMessage.Kind.OVERTOOK,
            receivedAtMillis = clock.millis()
        )
    }

    /** 마지막 표시가 러닝 중인지. 러닝이 끝난 뒤 늦게 온 근접·추월 메시지로 울리지 않게 한다. */
    private fun isRunning(): Boolean = synchronized(lock) { lastDisplay?.isRunning == true }

    private inline fun <T> decodeOrNull(message: WearMessage, decode: (ByteArray) -> T): T? = try {
        decode(message.data)
    } catch (e: SerializationException) {
        Timber.w(e, "%s 본문을 해독하지 못해 버린다", message.path)
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "%s 본문을 해독하지 못해 버린다", message.path)
        null
    }

    /** 폰 허용 플래그가 먼저다. 허용이 거짓이면 러닝 여부와 관계없이 멈추고 보내지 않은 심박을 버린다. */
    private fun applyCollectionRules(display: WatchDisplayMessage) {
        val allowed = display.isHeartRateCollectionAllowed
        synchronized(lock) {
            if (allowed && !collectionAllowed) allowedSinceMillis = clock.millis()
            if (!allowed) {
                if (collectionAllowed) allowGeneration++
                pending.clear()
                pendingSteps.clear()
                _latestHeartRate.value = null
            }
            collectionAllowed = allowed
        }
        collector.allow(allowed)
        if (display.isRunning && allowed) {
            collector.start()
            restartSilenceWatchdog()
        } else {
            collector.stop()
            cancelSilenceWatchdog()
        }
    }

    /** 러닝 중 표시가 오래 오지 않으면(폰이 끝냈는데 못 들은 경우) 배터리를 위해 수집을 멈춘다. */
    private fun restartSilenceWatchdog() {
        synchronized(lock) {
            silenceWatchdog?.cancel()
            silenceWatchdog = scope.launch {
                delay(MAX_SILENT_RUN_MILLIS)
                collector.stop()
                flushRequests.trySend(Unit)
            }
        }
    }

    private fun cancelSilenceWatchdog() {
        synchronized(lock) {
            silenceWatchdog?.cancel()
            silenceWatchdog = null
        }
    }

    /** 허용된 동안 잰 심박만 쌓는다. 전송은 기다리지 않고 신호만 보낸다. */
    private fun onSample(heartRate: WatchHeartRate) {
        val sample = heartRate.toContract()
        val full = synchronized(lock) {
            if (!collectionAllowed || sample.measuredAtEpochMillis < allowedSinceMillis) return
            pending.addLast(sample)
            trimPendingLocked()
            if (sample.status == HeartRateBatchMessage.Status.OK) _latestHeartRate.value = sample
            pending.size >= BATCH_SIZE
        }
        if (full) flushRequests.trySend(Unit)
    }

    /** 허용된 동안 잰 손목 걸음만 쌓는다. 심박 묶음과 함께 보낸다. */
    private fun onSteps(steps: WatchSteps) {
        synchronized(lock) {
            if (!collectionAllowed || steps.measuredAtEpochMillis < allowedSinceMillis) return
            pendingSteps.addLast(
                HeartRateBatchMessage.Step(
                    steps.measuredAtEpochMillis,
                    perMinute = steps.perMinute,
                    total = steps.total
                )
            )
            while (pendingSteps.size > MAX_PENDING_SAMPLES) pendingSteps.removeFirst()
        }
    }

    /**
     * 보내지 않은 심박을 [MAX_BATCH_SAMPLES]개씩 묶어 넘긴다(DataItem 크기 제한 100KB). 꺼낸 뒤 철회되었으면 보내지 않는다.
     * 저장에 실패하면 앞에 되돌려 다음 전송에 함께 보낸다(누락 0, NFR-032). 전송 코루틴 하나만 부른다.
     */
    private suspend fun flush() {
        while (true) {
            val (batch, generation) = synchronized(lock) {
                if (pending.isEmpty() && pendingSteps.isEmpty()) return
                val taken = List(minOf(pending.size, MAX_BATCH_SAMPLES)) { pending.removeFirst() }
                val steps = List(minOf(pendingSteps.size, MAX_BATCH_SAMPLES)) { pendingSteps.removeFirst() }
                HeartRateBatchMessage(taken, steps) to allowGeneration
            }
            if (isRevoked(generation)) return
            if (!sendHeartRates(batch)) {
                synchronized(lock) {
                    if (generation != allowGeneration || !collectionAllowed) return
                    batch.samples.asReversed().forEach { pending.addFirst(it) }
                    batch.steps.asReversed().forEach { pendingSteps.addFirst(it) }
                    trimPendingLocked()
                }
                return
            }
        }
    }

    private fun isRevoked(generation: Long): Boolean =
        synchronized(lock) { generation != allowGeneration || !collectionAllowed }

    /** 오래 보내지 못하면 메모리를 지키려 가장 오래된 심박부터 버린다. */
    private fun trimPendingLocked() {
        while (pending.size > MAX_PENDING_SAMPLES) pending.removeFirst()
        while (pendingSteps.size > MAX_PENDING_SAMPLES) pendingSteps.removeFirst()
    }

    private suspend fun refreshConnection(): List<String> {
        val nodes = dataLayer.connectedNodeIds()
        _phoneConnected.value = nodes.isNotEmpty()
        return nodes
    }

    private fun WatchHeartRate.toContract(): HeartRateBatchMessage.Sample = HeartRateBatchMessage.Sample(
        measuredAtEpochMillis = measuredAtEpochMillis,
        bpm = bpm,
        status = when (status) {
            WatchHeartRate.Status.OK -> HeartRateBatchMessage.Status.OK
            WatchHeartRate.Status.FAILED -> HeartRateBatchMessage.Status.FAILED
        }
    )

    internal companion object {
        /** 이만큼 모이면 바로 보낸다. */
        const val BATCH_SIZE = 10

        /** 묶음 크기가 안 되어도 이 간격마다 보낸다. */
        const val FLUSH_INTERVAL_MILLIS = 5_000L

        /** 폰 노드 연결 상태를 다시 확인하는 간격. */
        const val CONNECTION_POLL_MILLIS = 5_000L

        /** 폰에 닿은 표시 요청 뒤 다시 보내지 않는 간격. 앱 시작과 첫 화면 재개가 거의 함께 온다. */
        const val DISPLAY_REQUEST_GAP_MILLIS = 2_000L

        /** 러닝 중 표시가 이만큼 오지 않으면 수집을 멈춘다. */
        const val MAX_SILENT_RUN_MILLIS = 30 * 60 * 1_000L

        /** 묶음 하나에 넣는 최대 개수(약 20KB, DataItem 100KB 제한 안). */
        const val MAX_BATCH_SAMPLES = 300

        /** 보내지 못한 심박 상한(1초 1건 기준 3시간). DataClient는 끊겨도 저장되므로 보통은 닿지 않는다. */
        const val MAX_PENDING_SAMPLES = 3 * 60 * 60

        private const val DISPLAY_BUFFER = 8
    }
}
