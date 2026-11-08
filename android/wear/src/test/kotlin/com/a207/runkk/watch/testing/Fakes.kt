package com.a207.runkk.watch.testing

import com.a207.runkk.watch.alert.AlertBeeper
import com.a207.runkk.watch.alert.AlertVibrator
import com.a207.runkk.watch.alert.MonotonicClock
import com.a207.runkk.watch.alert.ProximityAlertPlayer
import com.a207.runkk.watch.heart.ExerciseOwner
import com.a207.runkk.watch.heart.ExerciseSession
import com.a207.runkk.watch.heart.HeartRateSensor
import com.a207.runkk.watch.heart.SensorPermission
import com.a207.runkk.watch.heart.WatchHeartRate
import com.a207.runkk.watch.heart.WatchSteps
import com.a207.runkk.watch.link.WearDataLayer
import com.a207.runkk.watch.link.WearMessage
import com.a207.runkk.watch.ui.NotificationPermissionGate
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
import kotlinx.coroutines.test.TestScope

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

    /** 남은 운동 세션 정리를 부른 횟수. */
    var leftoverChecks: Int = 0
        private set

    /** 참이면 남은 세션 정리가 Health Services 오류처럼 예외로 끝난다. */
    var failLeftoverCheck: Boolean = false
    private var channel: Channel<Result<WatchHeartRate>>? = null
    private val stepReadings = MutableSharedFlow<WatchSteps>(extraBufferCapacity = 64)

    override val steps: Flow<WatchSteps> get() = stepReadings

    /** 운동 세션이 잰 손목 걸음 하나를 흘린다(S15P31A207-242). */
    fun emitSteps(steps: WatchSteps) {
        stepReadings.tryEmit(steps)
    }

    override suspend fun endLeftoverSession(): Boolean {
        leftoverChecks++
        check(!failLeftoverCheck) { "Health Services 없음" }
        return false
    }

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

/**
 * 가짜 Health Services 운동 세션. 기기에 세션은 하나라 [owner]가 지금 주인이다. [events]에 열기·닫기·끝내기 순서를 남긴다.
 * 세션 흐름은 취소되면 세션을 끝내고(owner = NONE) 닫는다.
 */
class FakeExerciseSession(var owner: ExerciseOwner = ExerciseOwner.NONE) : ExerciseSession {
    val events = mutableListOf<String>()
    var opened: Int = 0
        private set
    var active: Int = 0
        private set
    var maxActive: Int = 0
        private set
    private var channel: Channel<WatchHeartRate>? = null

    override suspend fun owner(): ExerciseOwner = owner

    override suspend fun end() {
        events += "end:$owner"
        owner = ExerciseOwner.NONE
    }

    override fun heartRates(): Flow<WatchHeartRate> = flow {
        val readings = Channel<WatchHeartRate>(Channel.UNLIMITED)
        channel = readings
        opened++
        active++
        maxActive = maxOf(maxActive, active)
        owner = ExerciseOwner.THIS_APP
        events += "open"
        try {
            readings.consumeAsFlow().collect { emit(it) }
        } finally {
            active--
            owner = ExerciseOwner.NONE
            events += "close"
            channel = null
        }
    }

    fun emit(sample: WatchHeartRate) {
        checkNotNull(channel) { "세션이 열려 있지 않다" }.trySend(sample)
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

/** 가짜 알림 권한 문지기. [needed]가 참이면 묻고, [markAsked]가 불리면 다시 묻지 않는다. */
class FakeNotificationPermissionGate(var needed: Boolean = false) : NotificationPermissionGate {
    var asked: Int = 0
        private set

    override fun shouldAsk(): Boolean = needed && asked == 0

    override fun markAsked() {
        asked++
    }
}

/** 가짜 부팅 뒤 경과 시각의 시작값. 벽시계(T0)와 다른 값이라 재생기가 벽시계를 쓰면 시험이 틀린다. */
const val BOOT_ELAPSED: Long = 86_400_000

/** 코루틴 가상 시간을 따르는 단조 시계(SystemClock.elapsedRealtime 흉내). */
class SchedulerMonotonicClock(private val scheduler: TestCoroutineScheduler) : MonotonicClock {
    override fun elapsedMillis(): Long = BOOT_ELAPSED + scheduler.currentTime
}

/** 가짜 진동기·비프음. 낸 시각(가상 시간, 시험 시작 기준 ms)과 길이를 [events]에 순서대로 남긴다. */
class FakeAlertOutput(private val clock: MonotonicClock) :
    AlertVibrator,
    AlertBeeper {
    data class Event(val atMillis: Long, val kind: Kind, val durationMillis: Long)

    enum class Kind { VIBRATE, BEEP }

    val events = mutableListOf<Event>()

    /** 참이면 진동·비프가 기기 오류처럼 예외로 끝난다(시도한 것은 [events]에 남긴다). */
    var isFailing: Boolean = false

    override fun vibrate(durationMillis: Long) {
        events += Event(clock.elapsedMillis() - BOOT_ELAPSED, Kind.VIBRATE, durationMillis)
        check(!isFailing) { "진동기 오류" }
    }

    override fun beep(durationMillis: Int) {
        events += Event(clock.elapsedMillis() - BOOT_ELAPSED, Kind.BEEP, durationMillis.toLong())
        check(!isFailing) { "비프음 오류" }
    }

    /** 비프음 자원을 놓은 시각들. */
    val releases = mutableListOf<Long>()

    override fun release() {
        releases += clock.elapsedMillis() - BOOT_ELAPSED
    }

    /** 비프음을 낸 시각(T0 기준 ms). */
    fun beepTimes(): List<Long> = events.filter { it.kind == Kind.BEEP }.map { it.atMillis }

    /** 긴 진동(근접 반복의 짧은 진동보다 긴 것)을 낸 시각. */
    fun longVibrationTimes(minMillis: Long): List<Long> =
        events.filter { it.kind == Kind.VIBRATE && it.durationMillis >= minMillis }.map { it.atMillis }
}

/** 가짜 출력([output])으로 소리·진동을 남기는 근접 알림 재생기. */
fun TestScope.alertPlayer(
    output: FakeAlertOutput = FakeAlertOutput(SchedulerMonotonicClock(testScheduler))
): ProximityAlertPlayer = ProximityAlertPlayer(output, output, SchedulerMonotonicClock(testScheduler), backgroundScope)
