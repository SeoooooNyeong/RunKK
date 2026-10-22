package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * 조정자의 종료는 endRun으로 받는다(D-36, D-49). 워치의 종료 요청은 화면의 길게 누르기와 같은 규칙으로 받는다(자유런만).
 * 위치 콜백·1초 시계·워치 수집이 서로 다른 스레드에서 올 수 있어 측정 상태는 lock으로 보호하고,
 * 저장·덧붙이기 같은 suspend 작업은 주입받은 applicationScope에서 돈다.
 */
@Singleton
class RunTracker
@Inject
constructor(
    /** 기록 저장·늦은 심박 덧붙이기·전송 요청(1차 S-1: 보관 포트 둘과 재시도 상태를 묶은 협력 객체). */
    private val archive: RunArchive,
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

    /** 워치 시작 화면에 보일 오늘 계획(publishWatchHome). 러닝이 끝난 뒤 시작 화면 표시에도 쓴다. */
    @Volatile
    private var homePlan: PlanSnapshot? = null

    private val _progress = MutableStateFlow(RunProgress.INITIAL)
    val progress: StateFlow<RunProgress> = _progress.asStateFlow()

    private val _consentRequired = MutableStateFlow(false)
    val consentRequired: StateFlow<Boolean> = _consentRequired.asStateFlow()

    private val _watchStartRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val watchStartRequests: SharedFlow<Unit> = _watchStartRequests.asSharedFlow()

    init {
        scope.launch { watch.startRequests.collect { onWatchStartRequest() } }
        scope.launch { watch.endRequests.collect { onWatchEndRequest() } }
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

    /**
     * 측정을 시작한다. 포그라운드 측정 서비스를 띄우지 못하면(OS 거부) 만든 세션을 되돌리고 그 실패를 돌려준다:
     * 서비스 없이 측정 중으로 남으면 화면을 끄는 순간 위치가 끊긴다.
     */
    fun startRun(spec: RunStartSpec): Outcome<Unit> {
        val startable = checkStartable()
        if (startable is Outcome.Failure) return startable
        val created =
            synchronized(lock) {
                if (session?.isActive == true) {
                    return Outcome.Failure(AppError.Rejected(AppError.Rejected.RUN_IN_PROGRESS))
                }
                RunSession(spec).also { fresh ->
                    fresh.start(clock.instant())
                    session = fresh
                    locationFilter = LocationFilter()
                    coachingJudge = CoachingJudge.forMode(spec.mode)
                    _progress.value = RunProgress.INITIAL
                    heartRateJob?.cancel()
                    heartRateJob = scope.launch { watch.heartRates.collect { onHeartRates(it) } }
                }
            }
        val hosted = measurementHost.startForeground()
        if (hosted is Outcome.Failure) {
            synchronized(lock) {
                if (session === created) {
                    session = null
                    heartRateJob?.cancel()
                }
            }
            return hosted
        }
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
            if (session?.tryLock() == true) _progress.update { it.copy(isLocked = true) }
        }
    }

    fun onUnlockTapped() {
        val hint = synchronized(lock) { session?.unlockHintIfLocked() }
        hint?.let(guidanceOutput::play)
    }

    fun onUnlockLongPressed() {
        synchronized(lock) {
            if (session?.tryUnlock() == true) _progress.update { it.copy(isLocked = false) }
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

    /**
     * 대결 추월·역전 안내(FR-188)를 대결 음성·진동 설정으로 낸다(F-2). domain.battle은 domain.settings를 모르므로(5.1.2)
     * 설정을 아는 이 클래스가 방식을 정한다. 음성·진동이 모두 꺼져 있으면 화면에만 보인다.
     */
    fun onLeadChanged(kind: Guidance.Kind, message: GuidanceMessage, args: List<Any> = emptyList()) {
        val battle = settings.guidanceSettings()
        val guidance =
            if (battle.isBattleVoiceOn || battle.isBattleVibrationOn) {
                Guidance(
                    kind,
                    message,
                    args,
                    hasVoice = battle.isBattleVoiceOn,
                    hasVibration = battle.isBattleVibrationOn
                )
            } else {
                Guidance.screenOnly(kind, message, args)
            }
        guidanceOutput.play(guidance)
    }

    /**
     * 워치 시작 화면 표시(running = false, 오늘 계획, FR-042·N-21). WatchHomeSync가 앱 시작 때와 목표(오늘 계획·자유런 설정)가
     * 바뀔 때마다 부른다.
     * 측정 중이면 계획만 기억했다가 측정이 끝난 뒤 시작 화면에 보인다. 수집 허용 여부는 data.watch가 채운다.
     */
    fun publishWatchHome(plan: PlanSnapshot?) {
        homePlan = plan
        val measuring = synchronized(lock) { session?.isActive == true }
        if (!measuring) watch.publish(homeDisplay())
    }

    /**
     * 측정이 끝난 뒤 늦게 온 심박을 보관 기록에 덧붙인다(내부: WatchGateway.heartRates 구독만 부른다, 06-06, 1차 O-2).
     * 재시도·다음 묶음과 합치기는 RunArchive가 한다(F-4).
     */
    private fun onLateHeartRates(recordId: String, samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        scope.launch { archive.appendLateHeartRates(recordId, samples) }
    }

    /** 방금 끝난 세션의 시간 창을 기억한다. lock 안에서만 부른다. */
    private fun rememberFinished(finished: RunSession) {
        val start = finished.startedAt ?: return
        lastFinished = FinishedWindow(finished.spec.recordId, start, clock.instant())
    }

    /** 측정 서비스를 멈추고 워치를 시작 화면으로 돌린 뒤(S-18), 저장할 기록이면 보관한다. */
    private fun complete(outcome: FinishOutcome) {
        measurementHost.stop()
        watch.publish(homeDisplay())
        if (outcome is FinishOutcome.Saved) scope.launch { save(outcome.run) }
    }

    /** 보관하고 전송을 요청한다(RunArchive가 두 번 더 시도한다). 끝내 저장하지 못하면 화면 안내로 알린다(조용히 버리지 않는다). */
    private suspend fun save(run: CompletedRun) {
        if (!archive.save(run)) {
            guidanceOutput.play(Guidance.screenOnly(Guidance.Kind.SAVE_FAILED, GuidanceMessage.SAVE_FAILED))
        }
    }

    private fun onHeartRates(samples: List<HeartRateSample>) {
        val lateBatch =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive }
                val activeStart = active?.startedAt
                val window = lastFinished
                val lateSamples = mutableListOf<HeartRateSample>()
                samples.sortedBy { it.at }.forEach { sample ->
                    if (active != null && activeStart != null && !sample.at.isBefore(activeStart)) {
                        active.onHeartRate(sample)
                    } else if (window != null && window.contains(sample.at)) {
                        lateSamples += sample
                    }
                }
                window?.let { it.recordId to lateSamples }
            }
        lateBatch?.let { (recordId, lateSamples) -> onLateHeartRates(recordId, lateSamples) }
    }

    /**
     * 워치 시작 요청(FR-183). 측정 중이거나 러닝 화면 구독자가 없으면 버리고 워치에 안내를 보낸다.
     * 아니면 watchStartRequests로 내보내 러닝 화면이 시작 버튼과 같은 흐름을 탄다(design-review.md M43·M48).
     */
    private fun onWatchStartRequest() {
        val measuring = synchronized(lock) { session?.isActive == true }
        val notice =
            when {
                measuring -> WatchNotice.ALREADY_MEASURING
                _watchStartRequests.subscriptionCount.value == 0 -> WatchNotice.OPEN_RUN_SCREEN
                else -> null
            }
        if (notice == null) {
            _watchStartRequests.tryEmit(Unit)
            return
        }
        watch.publish(_progress.value.toWatchDisplay(plan = null, notice = notice, isRunning = measuring))
    }

    /**
     * 워치 종료 요청(워치에서 길게 누름, FR-181). 자유런이면 화면의 길게 누르기와 같은 규칙(잠금 중 거부)으로 끝낸다.
     * 대결·크루런은 서버 보고(그만두기)를 거쳐야 해서 폰에서만 끝내고 워치에 안내한다.
     */
    private fun onWatchEndRequest() {
        val mode = synchronized(lock) { session?.takeIf { it.isActive }?.spec?.mode } ?: return
        if (mode == RunMode.FREE) {
            onFinishLongPressed()
        } else {
            watch.publish(_progress.value.toWatchDisplay(plan = null, notice = WatchNotice.END_ON_PHONE))
        }
    }

    private fun homeDisplay(): WatchDisplay = WatchDisplay(
        isRunning = false,
        elapsed = Duration.ZERO,
        distance = Distance.ZERO,
        currentPace = null,
        heartRateBpm = null,
        todayPlan = homePlan,
        notice = null
    )

    private fun RunProgress.toWatchDisplay(
        plan: PlanSnapshot?,
        notice: WatchNotice?,
        isRunning: Boolean = true
    ): WatchDisplay = WatchDisplay(
        isRunning = isRunning,
        elapsed = elapsed,
        distance = distance,
        currentPace = currentPace,
        heartRateBpm = heartRate?.bpm,
        todayPlan = plan,
        notice = notice
    )

    private data class FinishedWindow(val recordId: String, val startedAt: Instant, val endedAt: Instant) {
        fun contains(at: Instant): Boolean = !at.isBefore(startedAt) && !at.isAfter(endedAt)
    }
}
