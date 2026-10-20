package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 한 번의 러닝 측정 흐름을 조정한다(6.1.14, D-56). 측정 상태를 가지므로 ui.run, data.device.MeasurementService,
 * 두 조정자가 같은 인스턴스를 받는다(@Singleton, design-review.md M49).
 *
 * 화면 입력은 누름 종류마다 다른 연산(onFinishTapped·onFinishLongPressed, lockScreen·onUnlockTapped·onUnlockLongPressed)으로,
 * 조정자의 종료는 endRun으로 받는다(D-36, D-49). 위치 콜백·1초 시계·워치 수집이 서로 다른 스레드에서 올 수 있어
 * 측정 상태는 lock으로 보호하고, 저장·덧붙이기 같은 suspend 작업은 주입받은 applicationScope에서 돈다.
 */
@Singleton
class RunTracker
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val uploadScheduler: UploadScheduler,
    private val watch: WatchGateway,
    private val guidanceOutput: GuidanceOutput,
    private val locationPermission: LocationPermission,
    private val measurementHost: MeasurementHost,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var session: RunSession? = null
    private var locationFilter = LocationFilter()
    private var coachingJudge = CoachingJudge.forMode(RunMode.FREE)
    private var heartRateJob: Job? = null

    /** 마지막으로 끝난 러닝(늦게 온 심박을 이 기록에 덧붙인다, FR-043). */
    private var lastFinished: FinishedWindow? = null

    private val _progress = MutableStateFlow(RunProgress.INITIAL)
    val progress: StateFlow<RunProgress> = _progress.asStateFlow()

    private val _consentRequired = MutableStateFlow(false)
    val consentRequired: StateFlow<Boolean> = _consentRequired.asStateFlow()

    private val _watchStartRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val watchStartRequests: SharedFlow<Unit> = _watchStartRequests.asSharedFlow()

    init {
        scope.launch { watch.startRequests.collect { onWatchStartRequest() } }
    }

    /** 카운트다운 전 확인: OS 위치 권한과 서버 위치 동의(FR-031, NFR-022). */
    fun checkStartable(): Outcome<Unit> {
        val allowed = locationPermission.granted() && settings.locationConsented()
        _consentRequired.value = !allowed
        return if (allowed) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT))
        }
    }

    fun startRun(spec: RunStartSpec): Outcome<Unit> {
        val startable = checkStartable()
        if (startable is Outcome.Failure) return startable
        synchronized(lock) {
            if (session?.isActive == true) {
                return Outcome.Failure(AppError.Rejected(AppError.Rejected.RUN_IN_PROGRESS))
            }
            session = RunSession(spec).also { it.start(clock.instant()) }
            locationFilter = LocationFilter()
            coachingJudge = CoachingJudge.forMode(spec.mode)
            _progress.value = RunProgress.INITIAL
            heartRateJob?.cancel()
            heartRateJob = scope.launch { watch.heartRates.collect { onHeartRates(it) } }
        }
        measurementHost.startForeground()
        return Outcome.Success(Unit)
    }

    fun onLocationSample(s: LocationSample) {
        synchronized(lock) {
            val active = session?.takeIf { it.isActive } ?: return
            locationFilter.accept(s)?.let(active::onLocation)
        }
    }

    fun tick(now: Instant) {
        // 설정 사본 읽기는 락 밖에서 한다(위치 콜백을 늦추지 않게).
        val birthYear = settings.birthYear()
        val guidanceSettings = settings.guidanceSettings()
        val (progress, guidances, plan) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return
                val progress = active.tick(now)
                val context =
                    GuidanceContext(
                        progress = progress,
                        todayPlan = active.spec.todayPlan,
                        gapMeters = active.gapMeters,
                        rank = active.rank,
                        birthYear = birthYear,
                        guidanceSettings = guidanceSettings
                    )
                // 잠금 연산의 locked 갱신과 순서가 뒤바뀌지 않도록 방출도 락 안에서 한다.
                _progress.value = progress
                Triple(progress, coachingJudge.evaluate(context), active.spec.todayPlan)
            }
        guidances.forEach(guidanceOutput::play)
        watch.publish(progress.toWatchDisplay(plan, notice = null))
    }

    fun lockScreen() {
        synchronized(lock) {
            val active = session?.takeIf { it.state == RunSession.State.RUNNING } ?: return
            active.lock()
            _progress.update { it.copy(locked = true) }
        }
    }

    fun onUnlockTapped() {
        val hint = synchronized(lock) { session?.takeIf { it.state == RunSession.State.LOCKED }?.hintUnlock() }
        hint?.let(guidanceOutput::play)
    }

    fun onUnlockLongPressed() {
        synchronized(lock) {
            val active = session?.takeIf { it.state == RunSession.State.LOCKED } ?: return
            active.unlock()
            _progress.update { it.copy(locked = false) }
        }
    }

    fun onFinishTapped() {
        val hint = synchronized(lock) { session?.takeIf { it.isActive }?.hintFinish() }
        hint?.let(guidanceOutput::play)
    }

    /** 화면의 종료(길게 누름). 잠금 중이면 거부하고 해제 안내를 낸다(FR-181, FR-182). */
    fun onFinishLongPressed(): FinishOutcome {
        val (outcome, hint) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return FinishOutcome.NotSaved
                val outcome = active.finish(clock.instant())
                if (outcome != FinishOutcome.Rejected) rememberFinished(active)
                outcome to if (outcome == FinishOutcome.Rejected) active.hintUnlock() else null
            }
        if (hint != null) {
            guidanceOutput.play(hint)
        } else {
            complete(outcome)
        }
        return outcome
    }

    /** 조정자의 종료(대결 종료 신호·도착·그만두기). 화면 연산을 부르지 않고 잠금과 무관하게 확정한다. */
    fun endRun(): FinishOutcome {
        val outcome =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return FinishOutcome.NotSaved
                active.end(clock.instant()).also { rememberFinished(active) }
            }
        complete(outcome)
        return outcome
    }

    fun onGapChanged(meters: Int) {
        synchronized(lock) { session?.takeIf { it.isActive }?.updateGap(meters) }
    }

    fun onRankChanged(rank: CrewRank) {
        synchronized(lock) { session?.takeIf { it.isActive }?.updateRank(rank) }
    }

    /** 측정이 끝난 뒤 늦게 온 심박을 보관 기록에 덧붙인다. appendHeartRates는 이 클래스만 부른다(design-review.md M32). */
    fun onLateHeartRates(recordId: String, samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        scope.launch { pendingRuns.appendHeartRates(recordId, samples) }
    }

    /** 방금 끝난 세션의 시간 창을 기억한다. lock 안에서만 부른다. */
    private fun rememberFinished(finished: RunSession) {
        val start = finished.startedAt ?: return
        lastFinished = FinishedWindow(finished.spec.recordId, start, clock.instant())
    }

    private fun complete(outcome: FinishOutcome) {
        measurementHost.stop()
        if (outcome is FinishOutcome.Saved) scope.launch { save(outcome.run) }
    }

    /**
     * 기기 보관함에 저장한 뒤 전송을 요청한다. 저장이 실패하면 짧게 두 번 더 시도하고,
     * 그래도 실패하면 화면 안내로 알린다(조용히 버리지 않는다).
     */
    private suspend fun save(run: CompletedRun) {
        repeat(SAVE_ATTEMPTS) { attempt ->
            if (pendingRuns.save(run) is Outcome.Success) {
                uploadScheduler.requestUpload()
                return
            }
            if (attempt < SAVE_ATTEMPTS - 1) delay(SAVE_RETRY_MILLIS * (attempt + 1))
        }
        guidanceOutput.play(Guidance.screenOnly(Guidance.Kind.SAVE_FAILED, "러닝 기록을 기기에 저장하지 못했어요"))
    }

    private fun onHeartRates(samples: List<HeartRateSample>) {
        val late =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive }
                val activeStart = active?.startedAt
                val window = lastFinished
                val late = mutableListOf<HeartRateSample>()
                samples.sortedBy { it.at }.forEach { sample ->
                    if (active != null && activeStart != null && !sample.at.isBefore(activeStart)) {
                        active.onHeartRate(sample)
                    } else if (window != null && window.contains(sample.at)) {
                        late += sample
                    }
                }
                window?.let { it.recordId to late }
            }
        late?.let { (recordId, samples) -> onLateHeartRates(recordId, samples) }
    }

    /**
     * 워치 시작 요청(FR-183). 측정 중이거나 러닝 화면 구독자가 없으면 버리고 워치에 안내를 보낸다.
     * 아니면 watchStartRequests로 내보내 러닝 화면이 시작 버튼과 같은 흐름을 탄다(design-review.md M43·M48).
     */
    private fun onWatchStartRequest() {
        val measuring = synchronized(lock) { session?.isActive == true }
        val notice =
            when {
                measuring -> "이미 측정 중이에요"
                _watchStartRequests.subscriptionCount.value == 0 -> "폰에서 러닝 화면을 여세요"
                else -> null
            }
        if (notice == null) {
            _watchStartRequests.tryEmit(Unit)
            return
        }
        watch.publish(_progress.value.toWatchDisplay(plan = null, notice = notice, running = measuring))
    }

    private fun RunProgress.toWatchDisplay(
        plan: PlanSnapshot?,
        notice: String?,
        running: Boolean = true
    ): WatchDisplay = WatchDisplay(
        running = running,
        elapsed = elapsed,
        distance = distance,
        currentPace = currentPace,
        heartRateBpm = heartRate?.bpm,
        todayPlan = plan,
        notice = notice
    )

    private companion object {
        const val SAVE_ATTEMPTS = 3
        const val SAVE_RETRY_MILLIS = 1_000L
    }

    private data class FinishedWindow(val recordId: String, val startedAt: Instant, val endedAt: Instant) {
        fun contains(at: Instant): Boolean = !at.isBefore(startedAt) && !at.isAfter(endedAt)
    }
}
