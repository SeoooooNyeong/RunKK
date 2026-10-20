package com.a207.runkk.watch.link

import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.heart.WatchHeartRate
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
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

/**
 * 폰과 메시지를 주고받는다(6.1.15, D-20). 메시지 형식은 wear.contract만 쓴다.
 *
 * - 폰 → 워치: 표시 정보(MessageClient, [WearPaths.WATCH_DISPLAY])를 [displays]로 내보내고,
 *   그 허용 플래그·러닝 여부로 [HeartRateCollector]를 켜고 끈다. 허용이 거짓이면 즉시 멈추고 보내지 않은 심박을 버린다(NFR-023).
 * - 워치 → 폰: 시작 요청(MessageClient, [WearPaths.START_REQUEST], FR-183)과
 *   심박 묶음(DataClient, [WearPaths.HEART_RATE_BATCH] 아래 묶음마다 다른 경로, FR-043, NFR-032).
 *
 * 수신(메시지·심박)은 상태만 바꾸고 바로 돌아오며, 전송은 따로 도는 코루틴이 맡는다.
 * 그래서 Data Layer가 늦어져도 철회 메시지 처리와 심박 수신이 막히지 않는다.
 * 워치는 기록을 만들지 않는다(C-002). 심박은 폰에 넘길 때까지 메모리에만 잠깐 둔다.
 */
@Singleton
class PhoneLink @Inject constructor(
    private val dataLayer: WearDataLayer,
    private val collector: HeartRateCollector,
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

    /** 아직 폰에 넘기지 않은 심박(측정 순서). */
    private val pending = ArrayDeque<HeartRateBatchMessage.Sample>()

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

    /** 폰이 수집을 허용했지만 워치 OS 심박 권한이 없다. */
    val heartRatePermissionNeeded: StateFlow<Boolean> = collector.state
        .map { it == HeartRateCollector.State.NEEDS_PERMISSION }
        .stateIn(scope, SharingStarted.Eagerly, collector.state.value == HeartRateCollector.State.NEEDS_PERMISSION)

    private val _latestHeartRate = MutableStateFlow<HeartRateBatchMessage.Sample?>(null)

    /** 워치가 마지막으로 잰 정상 심박(화면 표시용). 수집 금지가 오면 지운다. */
    val latestHeartRate: StateFlow<HeartRateBatchMessage.Sample?> = _latestHeartRate.asStateFlow()

    /** 수신·수집·전송·연결 확인을 시작한다. 앱 시작 때 한 번 부르고, 거듭 불러도 한 번만 돈다. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { dataLayer.messages.collect { onMessage(it) } }
        scope.launch { collector.samples.collect { onSample(it) } }
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
    }

    /** 자유런 시작 요청(FR-183). 폰과 연결되어 있지 않거나 보내지 못하면 거짓("폰과 연결 필요"). */
    suspend fun sendStartRequest(): Boolean {
        val nodes = refreshConnection()
        if (nodes.isEmpty()) return false
        val bytes = StartRequestMessage(requestedAtEpochMillis = clock.millis()).encode()
        var delivered = false
        nodes.forEach { node -> delivered = dataLayer.sendMessage(node, WearPaths.START_REQUEST, bytes) || delivered }
        return delivered
    }

    /**
     * 심박 묶음을 DataClient로 넘긴다. 묶음마다 다른 경로에 넣어, 끊긴 동안 쌓인 묶음이 서로 덮어쓰지 않고
     * 연결되면 모두 동기화된다(NFR-032). 기기에 저장했으면 참.
     */
    suspend fun sendHeartRates(batch: HeartRateBatchMessage): Boolean {
        if (batch.samples.isEmpty()) return true
        val sequence = synchronized(lock) { batchSequence++ }
        val path = "${WearPaths.HEART_RATE_BATCH}/${batch.samples.first().measuredAtEpochMillis}-$sequence"
        return dataLayer.putData(path, batch.encode())
    }

    /** OS 심박 권한 창에 쓸 권한 이름(watch.ui가 watch.heart를 모르게 여기서 넘긴다). */
    val heartRatePermissionName: String get() = collector.permissionName

    /** OS 심박 권한 결과가 바뀌었다(권한 창 닫힘, 화면 복귀). */
    fun onHeartRatePermissionChanged() = collector.onPermissionChanged()

    private fun onMessage(message: WearMessage) {
        if (message.path != WearPaths.WATCH_DISPLAY) return
        val display = decodeOrNull(message.data) ?: return
        _phoneConnected.value = true
        applyCollectionRules(display)
        _lastDisplayAtMillis.value = clock.millis()
        _displays.tryEmit(display)
        if (!display.running) flushRequests.trySend(Unit)
    }

    private fun decodeOrNull(bytes: ByteArray): WatchDisplayMessage? = try {
        WatchDisplayMessage.decode(bytes)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** 폰 허용 플래그가 먼저다. 허용이 거짓이면 러닝 여부와 관계없이 멈추고 보내지 않은 심박을 버린다. */
    private fun applyCollectionRules(display: WatchDisplayMessage) {
        val allowed = display.heartRateCollectionAllowed
        synchronized(lock) {
            if (allowed && !collectionAllowed) allowedSinceMillis = clock.millis()
            if (!allowed) {
                if (collectionAllowed) allowGeneration++
                pending.clear()
                _latestHeartRate.value = null
            }
            collectionAllowed = allowed
        }
        collector.allow(allowed)
        if (display.running && allowed) {
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

    /**
     * 보내지 않은 심박을 [MAX_BATCH_SAMPLES]개씩 묶어 넘긴다(DataItem 크기 제한 100KB). 꺼낸 뒤 철회되었으면 보내지 않는다.
     * 저장에 실패하면 앞에 되돌려 다음 전송에 함께 보낸다(누락 0, NFR-032). 전송 코루틴 하나만 부른다.
     */
    private suspend fun flush() {
        while (true) {
            val (batch, generation) = synchronized(lock) {
                if (pending.isEmpty()) return
                val taken = List(minOf(pending.size, MAX_BATCH_SAMPLES)) { pending.removeFirst() }
                taken to allowGeneration
            }
            if (isRevoked(generation)) return
            if (!sendHeartRates(HeartRateBatchMessage(batch))) {
                synchronized(lock) {
                    if (generation != allowGeneration || !collectionAllowed) return
                    batch.asReversed().forEach { pending.addFirst(it) }
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

        /** 러닝 중 표시가 이만큼 오지 않으면 수집을 멈춘다. */
        const val MAX_SILENT_RUN_MILLIS = 30 * 60 * 1_000L

        /** 묶음 하나에 넣는 최대 개수(약 20KB, DataItem 100KB 제한 안). */
        const val MAX_BATCH_SAMPLES = 300

        /** 보내지 못한 심박 상한(1초 1건 기준 3시간). DataClient는 끊겨도 저장되므로 보통은 닿지 않는다. */
        const val MAX_PENDING_SAMPLES = 3 * 60 * 60

        private const val DISPLAY_BUFFER = 8
    }
}
