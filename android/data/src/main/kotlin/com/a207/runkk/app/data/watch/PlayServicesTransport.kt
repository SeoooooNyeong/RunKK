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
 * Play 서비스 Wearable API 송수신. 앱 프로세스가 살아 있는 동안 리스너를 단다(측정 중에는 MeasurementService가 프로세스를 살린다).
 * 심박 데이터 항목은 읽은 뒤 지워 다시 오지 않게 하고, 앱이 꺼져 있던 동안 쌓인 항목은 시작 때 한 번 읽는다.
 * 워치가 없거나 Play 서비스가 없으면 보내기는 조용히 버린다(측정은 폰만으로 계속된다).
 */
internal class PlayServicesTransport(context: Context, private val scope: CoroutineScope) : WatchLink.Transport {
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
     * 보낼 메시지(경로, 내용)를 한 코루틴이 차례로 보낸다(순서가 뒤바뀌지 않게). 표시 정보(1초마다)와 근접 알림(2초마다 유지 신호)은
     * 밀리면 경로마다 최신 것만 남기고, 추월·역전(LEAD_CHANGE) 같은 일회성 메시지는 버리지 않는다(리뷰 A2-H1: 근접 알림 끔 유실 방지).
     * [wakeUp]은 보낼 것이 생겼다는 신호다.
     * init 블록보다 먼저 선언한다: init의 코루틴이 바로 돌면 아직 만들어지지 않은 값을 읽어 죽는다(PlayServicesTransportTest).
     */
    private val pending = ArrayDeque<Pair<String, ByteArray>>()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var nodeIds: List<String> = emptyList()

    init {
        messages.addListener(::onMessage)
        data.addListener(::onData)
        scope.launch { quietly { drainPendingHeartRates() } }
        scope.launch {
            for (signal in wakeUp) {
                while (true) {
                    val (path, payload) = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                    quietly { deliver(path, payload) }
                }
            }
        }
        // 워치 연결·끊김은 런크크 워치 앱 능력(WATCH_CAPABILITY)이 닿는 노드가 바뀔 때만 받는다(리뷰 N-5: 5초 조회 대신).
        // 시작 때 한 번 조회해 이미 연결된 워치를 찾는다.
        capabilities.addListener(::onCapabilityChanged, WearPaths.WATCH_CAPABILITY)
        scope.launch { quietly { refreshNodes() } }
    }

    override fun send(path: String, payload: ByteArray) {
        synchronized(pending) {
            if (path in LATEST_ONLY) pending.removeAll { it.first == path }
            pending.addLast(path to payload)
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
        wakeUp.trySend(Unit)
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

        /** 밀리면 최신 1건만 보내는 경로(상태를 통째로 다시 보내는 메시지). */
        val LATEST_ONLY = setOf(WearPaths.WATCH_DISPLAY, WearPaths.PROXIMITY)

        /** 워치가 오래 닿지 않을 때 쌓이는 상한. */
        const val MAX_PENDING = 32
    }
}
