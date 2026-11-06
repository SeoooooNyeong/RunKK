package com.a207.runkk.app.domain.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.GpsWarmUp
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.GuideCopy
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

val T0: Instant = Instant.parse("2026-10-20T00:00:00Z")

/** 시험에서 앞으로 돌릴 수 있는 고정 시계. */
class TestClock(var now: Instant = T0) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

/** 여러 가짜 포트의 호출 순서를 한 곳에 남긴다. */
class CallLog {
    val calls = mutableListOf<String>()

    fun add(call: String) {
        calls += call
    }
}

class FakePendingRunRepository(private val log: CallLog = CallLog()) : PendingRunRepository {
    val runs = mutableListOf<CompletedRun>()
    val appended = mutableListOf<Pair<String, List<HeartRateSample>>>()
    var failWith: AppError? = null

    /** 늦은 심박 덧붙이기만 실패시킨다(F-4). */
    var appendFailWith: AppError? = null
    private val flow = MutableStateFlow<List<CompletedRun>>(emptyList())

    private fun result(call: String): Outcome<Unit> {
        log.add(call)
        return failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    /** 앞으로 이만큼의 save를 Storage 실패로 돌려준다(그 뒤는 failWith 규칙). */
    var saveFailuresLeft = 0

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        if (saveFailuresLeft > 0) {
            saveFailuresLeft--
            log.add("pending.save")
            return Outcome.Failure(AppError.Storage)
        }
        return saveResult(run)
    }

    private fun saveResult(run: CompletedRun): Outcome<Unit> = result("pending.save").also {
        if (it is Outcome.Success) {
            runs += run
            flow.value = runs.toList()
        }
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> {
        log.add("pending.pending")
        return failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(runs.toList())
    }

    override fun observePending(): Flow<List<CompletedRun>> = flow

    override suspend fun remove(recordId: String): Outcome<Unit> = result("pending.remove:$recordId").also {
        if (it is Outcome.Success) {
            runs.removeAll { run -> run.recordId == recordId }
            flow.value = runs.toList()
        }
    }

    /** 실제 보관소처럼 상태와 사유만 바꾸고 보관함에 남긴다(R12 D2-b). */
    override suspend fun markRejected(run: CompletedRun, reason: String): Outcome<Unit> =
        result("pending.markRejected:${run.recordId}").also {
            if (it is Outcome.Success) {
                runs.replaceAll { kept ->
                    if (kept.recordId == run.recordId) {
                        kept.copy(uploadState = UploadState.REJECTED, rejectedReason = reason)
                    } else {
                        kept
                    }
                }
                flow.value = runs.toList()
            }
        }

    /** 실제 보관소처럼 REJECTED만 미전송으로 되돌리고 사유를 비운다(W40). */
    override suspend fun resetRejected(): Outcome<Int> {
        log.add("pending.resetRejected")
        failWith?.let { return Outcome.Failure(it) }
        val count = runs.count { it.uploadState == UploadState.REJECTED }
        runs.replaceAll { kept ->
            if (kept.uploadState == UploadState.REJECTED) {
                kept.copy(uploadState = UploadState.NOT_SENT, rejectedReason = null)
            } else {
                kept
            }
        }
        flow.value = runs.toList()
        return Outcome.Success(count)
    }

    override suspend fun purgeRoutes(): Outcome<Unit> = result("pending.purgeRoutes")

    override suspend fun purgeHeartRates(): Outcome<Unit> = result("pending.purgeHeartRates")

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> =
        Outcome.Success(runs.count { it.uploadState != UploadState.REJECTED } > 20)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> {
        log.add("pending.appendHeartRates:$recordId")
        appendFailWith?.let { return Outcome.Failure(it) }
        appended += recordId to samples
        return Outcome.Success(Unit)
    }

    /** 머리 정보 없는 기록의 늦은 심박 항목(D-77). */
    val lateEntries = mutableListOf<LateHeartRates>()

    /** 늦은 심박 항목 읽기만 실패시킨다. */
    var lateReadFailWith: AppError? = null

    /** 늦은 심박 항목 지우기만 실패시킨다. */
    var lateRemoveFailWith: AppError? = null

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> {
        log.add("pending.pendingLateHeartRates")
        return lateReadFailWith?.let { Outcome.Failure(it) } ?: Outcome.Success(lateEntries.toList())
    }

    /** removeLateHeartRates가 받은 항목(보낸 내용). */
    val lateRemoved = mutableListOf<LateHeartRates>()

    /** 실제 보관소처럼 보낸 심박만 빼고, 남는 심박이 없으면 항목을 지운다(X-1). */
    override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> {
        log.add("pending.removeLateHeartRates:${sent.recordId}")
        lateRemoveFailWith?.let { return Outcome.Failure(it) }
        lateRemoved += sent
        val sentAts = sent.samples.map { it.at }.toSet()
        val updated =
            lateEntries.mapNotNull { entry ->
                if (entry.recordId != sent.recordId) return@mapNotNull entry
                entry.copy(samples = entry.samples.filter { it.at !in sentAts }).takeIf { it.samples.isNotEmpty() }
            }
        lateEntries.clear()
        lateEntries += updated
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> = result("pending.clear")
}

class FakeUploadScheduler(private val log: CallLog = CallLog()) : UploadScheduler {
    var requests = 0

    override fun requestUpload() {
        requests++
        log.add("upload.requestUpload")
    }
}

class FakeWatchGateway : WatchGateway {
    /** 여러 스레드가 함께 보낼 수 있다(RunTrackerRaceTest). */
    val published: MutableList<WatchDisplay> = Collections.synchronizedList(mutableListOf())
    override val startRequests = MutableSharedFlow<Unit>()
    override val endRequests = MutableSharedFlow<Unit>()
    override val pauseRequests = MutableSharedFlow<WatchGateway.PauseAction>()
    override val heartRates = MutableSharedFlow<List<HeartRateSample>>()
    var connected = true

    /** 짝지은 워치가 스스로 측정 중인가(WATCH_MEASURING, D-373). */
    var watchMeasuring = false

    /** publishProximity로 보낸 값(null = 끔). */
    val proximity = mutableListOf<ProximityAlert?>()
    val leadChanges = mutableListOf<Guidance.Kind>()

    override fun publish(d: WatchDisplay) {
        published += d
    }

    override fun isConnected(): Boolean = connected

    override fun isWatchMeasuring(): Boolean = watchMeasuring

    override fun publishProximity(alert: ProximityAlert?) {
        proximity += alert
    }

    override fun publishLeadChange(kind: Guidance.Kind) {
        leadChanges += kind
    }
}

/** 협력 객체 넷으로 RunTracker를 조립한다(D-95). */
fun runTracker(
    pending: PendingRunRepository,
    scheduler: UploadScheduler,
    watch: WatchGateway,
    output: GuidanceOutput,
    permission: LocationPermission,
    host: MeasurementHost,
    settings: SettingsRepository,
    clock: Clock,
    scope: CoroutineScope
): RunTracker = RunTracker(
    RunArchive(pending, scheduler, scope),
    RunWatchBridge(watch, scope),
    RunCoach(output, settings),
    MeasurementRuntime(permission, host, settings, clock)
)

class FakeGuidanceOutput : GuidanceOutput {
    /** 여러 스레드가 함께 낼 수 있다(RunTrackerRaceTest). */
    val played: MutableList<Guidance> = Collections.synchronizedList(mutableListOf())

    override fun play(g: Guidance) {
        played += g
    }
}

class FakeLocationPermission(var granted: Boolean = true) : LocationPermission {
    override fun granted(): Boolean = granted
}

/** GPS 예열 가짜(W55): [emit]으로 상태를 내고, 지금 모으는 쪽 수([collectors])와 받은 정확도 기준을 남긴다. */
class FakeGpsWarmUp : GpsWarmUp {
    private val updates = MutableSharedFlow<GpsReadiness>(extraBufferCapacity = 8)
    var collectors = 0
        private set
    var maxAccuracyMeters: Double? = null
        private set

    fun emit(readiness: GpsReadiness) {
        updates.tryEmit(readiness)
    }

    override fun readiness(maxAccuracyMeters: Double): Flow<GpsReadiness> = flow {
        this@FakeGpsWarmUp.maxAccuracyMeters = maxAccuracyMeters
        collectors++
        try {
            emitAll(updates)
        } finally {
            collectors--
        }
    }
}

class FakeMeasurementHost : MeasurementHost {
    var started = 0
    var stopped = 0
    var startFailure: AppError? = null

    override fun startForeground(): Outcome<Unit> {
        started++
        return startFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override fun stop() {
        stopped++
    }
}

class FakeSettingsRepository(
    snapshot: SettingsSnapshot = SettingsSnapshot.SAFE.copy(isLocationConsented = true),
    private val log: CallLog = CallLog()
) : SettingsRepository {
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    private val flow = MutableStateFlow(snapshot)

    /** 설정 사본. 바꾸면 changes()도 방출한다. */
    var snapshot: SettingsSnapshot
        get() = flow.value
        set(value) {
            flow.value = value
        }

    override suspend fun refresh(): Outcome<Unit> {
        log.add("settings.refresh")
        return refreshResult
    }

    override fun locationConsented(): Boolean = snapshot.isLocationConsented

    override fun heartRateConsented(): Boolean = snapshot.isHeartRateConsented

    override fun watchLinked(): Boolean = snapshot.isWatchLinked

    override fun courseMapShared(): Boolean = snapshot.isCourseMapShared

    override fun birthYear(): Int? = snapshot.birthYear

    override fun guidanceSettings(): GuidanceSettings = snapshot.guidanceSettings

    override fun isGuest(): Boolean = snapshot.isGuest

    override fun changes(): Flow<SettingsSnapshot> = flow

    override suspend fun clear(): Outcome<Unit> {
        log.add("settings.clear")
        return Outcome.Success(Unit)
    }

    /** 첫 사용 가이드 기기 사본(D-277). */
    var guide: GuideCopy = GuideCopy.NONE

    override suspend fun guideCopy(): GuideCopy = guide

    override suspend fun saveGuideCopy(copy: GuideCopy): Outcome<Unit> {
        log.add("settings.saveGuideCopy:$copy")
        guide = copy
        return Outcome.Success(Unit)
    }
}
