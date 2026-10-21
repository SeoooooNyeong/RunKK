package com.a207.runkk.app.data.watch

import android.content.Context
import android.net.Uri
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
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
 */
@Singleton
class WatchLink internal constructor(
    private val transport: Transport,
    private val settings: SettingsRepository,
    scope: CoroutineScope
) : WatchGateway {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        scope: CoroutineScope
    ) : this(PlayServicesTransport(context, scope), settings, scope)

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
                .map { it.heartRateConsented && it.watchLinked }
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
        transport.send(WearPaths.WATCH_DISPLAY, d.toMessage(collectionAllowed()).encode())
    }

    override fun isConnected(): Boolean = transport.connected.value

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
            synchronized(lock) { lastDisplay }?.toMessage(allowed)
                ?: WatchDisplayMessage(
                    running = false,
                    elapsedSeconds = 0,
                    distanceMeters = 0,
                    heartRateCollectionAllowed = allowed
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

internal fun WatchDisplay.toMessage(heartRateCollectionAllowed: Boolean): WatchDisplayMessage = WatchDisplayMessage(
    running = running,
    elapsedSeconds = elapsed.seconds,
    distanceMeters = distance.meters,
    currentPaceSecondsPerKm = currentPace?.secondsPerKm,
    heartRateBpm = heartRateBpm,
    planDistanceMeters = todayPlan?.targetDistance?.meters,
    planPaceSecondsPerKm = todayPlan?.targetPace?.secondsPerKm,
    heartRateCollectionAllowed = heartRateCollectionAllowed,
    notice = notice
)

internal fun HeartRateBatchMessage.Sample.toDomain(): HeartRateSample =
    HeartRateSample(Instant.ofEpochMilli(measuredAtEpochMillis), bpm)

/**
 * Play 서비스 Wearable API 송수신. 앱 프로세스가 살아 있는 동안 리스너를 단다(측정 중에는 MeasurementService가 프로세스를 살린다).
 * 심박 데이터 항목은 읽은 뒤 지워 다시 오지 않게 하고, 앱이 꺼져 있던 동안 쌓인 항목은 시작 때 한 번 읽는다.
 * 워치가 없거나 Play 서비스가 없으면 보내기는 조용히 버린다(측정은 폰만으로 계속된다).
 */
private class PlayServicesTransport(context: Context, private val scope: CoroutineScope) : WatchLink.Transport {
    private val messages: MessageClient = Wearable.getMessageClient(context)
    private val data: DataClient = Wearable.getDataClient(context)
    private val capabilities: CapabilityClient = Wearable.getCapabilityClient(context)

    private val connection = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = connection.asStateFlow()

    private val received =
        MutableSharedFlow<WatchLink.Incoming>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    override val incoming: SharedFlow<WatchLink.Incoming> = received.asSharedFlow()

    /**
     * 보낼 메시지(경로, 내용). 표시 정보는 1초마다 오므로 밀리면 최신 것만 보낸다(순서가 뒤바뀌지 않게 한 코루틴이 보낸다).
     * init 블록보다 먼저 선언한다: init의 코루틴이 바로 돌면 아직 만들어지지 않은 채널을 읽어 죽는다(PlayServicesTransportTest).
     */
    private val outbox = Channel<Pair<String, ByteArray>>(Channel.CONFLATED)

    @Volatile
    private var nodeIds: List<String> = emptyList()

    init {
        messages.addListener(::onMessage)
        data.addListener(::onData)
        scope.launch { quietly { drainPendingHeartRates() } }
        scope.launch {
            for ((path, payload) in outbox) quietly { deliver(path, payload) }
        }
        // 워치 연결·끊김은 런크크 워치 앱 능력(WATCH_CAPABILITY)이 닿는 노드가 바뀔 때만 받는다(리뷰 N-5: 5초 조회 대신).
        // 시작 때 한 번 조회해 이미 연결된 워치를 찾는다.
        capabilities.addListener(::onCapabilityChanged, WearPaths.WATCH_CAPABILITY)
        scope.launch { quietly { refreshNodes() } }
    }

    override fun send(path: String, payload: ByteArray) {
        outbox.trySend(path to payload)
    }

    /** 워치가 없으면(닿는 노드 0) 보내지 않고 버린다. 노드 목록은 능력 변화·워치 메시지로 갱신된다. */
    private suspend fun deliver(path: String, payload: ByteArray) {
        nodeIds.forEach { nodeId -> messages.sendMessage(nodeId, path, payload).await() }
    }

    /** 워치가 보낸 메시지는 그 노드가 닿는다는 뜻이다(능력 알림보다 먼저 올 수 있다). */
    private fun onMessage(event: MessageEvent) {
        if (event.sourceNodeId !in nodeIds) updateNodes(nodeIds + event.sourceNodeId)
        received.tryEmit(WatchLink.Incoming(event.path, event.data))
    }

    private fun onCapabilityChanged(info: CapabilityInfo) {
        updateNodes(info.nodes.map { it.id })
    }

    private fun onData(events: DataEventBuffer) {
        val items =
            events.use { buffer ->
                buffer
                    .filter { it.type == DataEvent.TYPE_CHANGED }
                    .map { it.dataItem.freeze() }
                    .filter { it.uri.path.orEmpty().startsWith(WearPaths.HEART_RATE_BATCH) }
            }
        items.forEach { item ->
            item.data?.let { received.tryEmit(WatchLink.Incoming(item.uri.path.orEmpty(), it)) }
            data.deleteDataItems(item.uri)
        }
    }

    private suspend fun refreshNodes() {
        val info = capabilities.getCapability(WearPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
        updateNodes(info.nodes.map { it.id })
    }

    private fun updateNodes(ids: List<String>) {
        nodeIds = ids
        connection.value = ids.isNotEmpty()
    }

    private suspend fun drainPendingHeartRates() {
        val pattern = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WearPaths.HEART_RATE_BATCH).build()
        val buffer = data.getDataItems(pattern, DataClient.FILTER_PREFIX).await()
        val items = buffer.use { all -> all.map { it.freeze() } }
        items.forEach { item ->
            item.data?.let { received.tryEmit(WatchLink.Incoming(item.uri.path.orEmpty(), it)) }
            data.deleteDataItems(item.uri)
        }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            // Play 서비스·워치 없음(ApiException), 연결 끊김: 워치 없이도 측정은 계속된다. 원인은 기록으로 남긴다(리뷰 N-6).
            Timber.d(e, "워치 통신 실패")
        }
    }

    private companion object {
        const val BUFFER = 64
    }
}
