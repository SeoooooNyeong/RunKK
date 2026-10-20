package com.a207.runkk.app.domain.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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
    private val flow = MutableStateFlow<List<CompletedRun>>(emptyList())

    private fun result(call: String): Outcome<Unit> {
        log.add(call)
        return failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun save(run: CompletedRun): Outcome<Unit> = result("pending.save").also {
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
        if (it is Outcome.Success) runs.removeAll { run -> run.recordId == recordId }
    }

    override suspend fun purgeRoutes(): Outcome<Unit> = result("pending.purgeRoutes")

    override suspend fun purgeHeartRates(): Outcome<Unit> = result("pending.purgeHeartRates")

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = Outcome.Success(runs.size >= 20)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
        result("pending.appendHeartRates:$recordId").also { appended += recordId to samples }

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
    val published = mutableListOf<WatchDisplay>()
    override val startRequests = MutableSharedFlow<Unit>()
    override val heartRates = MutableSharedFlow<List<HeartRateSample>>()
    var connected = true

    override fun publish(d: WatchDisplay) {
        published += d
    }

    override fun isConnected(): Boolean = connected
}

class FakeGuidanceOutput : GuidanceOutput {
    val played = mutableListOf<Guidance>()

    override fun play(g: Guidance) {
        played += g
    }
}

class FakeLocationPermission(var granted: Boolean = true) : LocationPermission {
    override fun granted(): Boolean = granted
}

class FakeMeasurementHost : MeasurementHost {
    var started = 0
    var stopped = 0

    override fun startForeground() {
        started++
    }

    override fun stop() {
        stopped++
    }
}

class FakeSettingsRepository(
    var snapshot: SettingsSnapshot = SettingsSnapshot.SAFE.copy(locationConsented = true),
    private val log: CallLog = CallLog()
) : SettingsRepository {
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    private val flow = MutableStateFlow(snapshot)

    override suspend fun refresh(): Outcome<Unit> {
        log.add("settings.refresh")
        return refreshResult
    }

    override fun locationConsented(): Boolean = snapshot.locationConsented

    override fun heartRateConsented(): Boolean = snapshot.heartRateConsented

    override fun watchLinked(): Boolean = snapshot.watchLinked

    override fun courseMapShared(): Boolean = snapshot.courseMapShared

    override fun birthYear(): Int? = snapshot.birthYear

    override fun guidanceSettings(): GuidanceSettings = snapshot.guidanceSettings

    override fun changes(): Flow<SettingsSnapshot> = flow

    override suspend fun clear(): Outcome<Unit> {
        log.add("settings.clear")
        return Outcome.Success(Unit)
    }
}
