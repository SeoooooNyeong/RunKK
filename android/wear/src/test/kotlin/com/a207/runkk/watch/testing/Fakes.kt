package com.a207.runkk.watch.testing

import com.a207.runkk.watch.heart.HeartRateSensor
import com.a207.runkk.watch.heart.SensorPermission
import com.a207.runkk.watch.heart.WatchHeartRate
import com.a207.runkk.watch.link.WearDataLayer
import com.a207.runkk.watch.link.WearMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler

/** 시험 시작 시각(2026-10-20 근처). 가상 시간과 함께 흐른다. */
const val T0: Long = 1_792_000_000_000

/** 코루틴 가상 시간을 따르는 시계. */
class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(T0 + scheduler.currentTime)
}

/** 가짜 심박 센서. 구독 수로 측정이 켜졌는지 확인하고, [emit]·[fail]로 값과 오류를 흘린다. */
class FakeHeartRateSensor : HeartRateSensor {
    var subscribers: Int = 0
        private set
    var openedSessions: Int = 0
        private set
    private var channel: Channel<Result<WatchHeartRate>>? = null

    override fun heartRates(): Flow<WatchHeartRate> = flow {
        val readings = Channel<Result<WatchHeartRate>>(Channel.UNLIMITED)
        channel = readings
        subscribers++
        openedSessions++
        try {
            readings.consumeAsFlow().collect { emit(it.getOrThrow()) }
        } finally {
            subscribers--
            channel = null
        }
    }

    fun emit(sample: WatchHeartRate) {
        checkNotNull(channel) { "센서를 구독하지 않았다" }.trySend(Result.success(sample))
    }

    fun fail() {
        checkNotNull(channel) { "센서를 구독하지 않았다" }.trySend(Result.failure(IllegalStateException("센서 없음")))
    }
}

class FakeSensorPermission(var isGranted: Boolean = true) : SensorPermission {
    override val name: String = "test.permission.HEART_RATE"

    override fun granted(): Boolean = isGranted
}

/**
 * 가짜 Wearable Data Layer. MessageClient는 연결된 노드에만 보내고, DataClient는 끊겨도 받아 둔다(D-20, NFR-032):
 * [putItems]는 워치가 넣은 항목, [deliveredItems]는 폰에 동기화된 항목이다.
 */
class FakeWearDataLayer : WearDataLayer {
    val incoming = MutableSharedFlow<WearMessage>(extraBufferCapacity = 64)
    var nodes: List<String> = listOf("phone")
        set(value) {
            field = value
            if (value.isNotEmpty()) syncData()
        }
    var failPutData: Boolean = false
    var failSendMessage: Boolean = false

    /** 있으면 putData가 이 신호를 받을 때까지 멈춘다(Play 서비스 지연 흉내). */
    var putGate: CompletableDeferred<Unit>? = null
    val sentMessages = mutableListOf<Triple<String, String, ByteArray>>()
    val putItems = mutableListOf<Pair<String, ByteArray>>()
    val deliveredItems = mutableListOf<Pair<String, ByteArray>>()

    override val messages: Flow<WearMessage> = incoming

    override suspend fun connectedNodeIds(): List<String> = nodes

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean {
        if (failSendMessage || nodeId !in nodes) return false
        sentMessages += Triple(nodeId, path, data)
        return true
    }

    override suspend fun putData(path: String, data: ByteArray): Boolean {
        putGate?.await()
        if (failPutData) return false
        putItems += path to data
        if (nodes.isNotEmpty()) syncData()
        return true
    }

    private fun syncData() {
        putItems.filter { item -> deliveredItems.none { it.first == item.first } }.forEach { deliveredItems += it }
    }
}

fun ok(atMillis: Long, bpm: Int): WatchHeartRate = WatchHeartRate(atMillis, bpm, WatchHeartRate.Status.OK)

fun failed(atMillis: Long): WatchHeartRate = WatchHeartRate(atMillis, 0, WatchHeartRate.Status.FAILED)
