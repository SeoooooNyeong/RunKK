package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 한 번의 러닝 측정 흐름을 조정한다(6.1.14, D-56). 측정 상태를 가지므로 ui.run, data.device.MeasurementService,
 * 두 조정자가 같은 인스턴스를 받는다(@Singleton, design-review.md M49).
 *
 * 생성자 의존은 책임이 한 문장인 협력 객체 넷이다(D-95): 보관(RunArchive), 워치(RunWatchBridge), 안내(RunCoach),
 * 실행 환경(MeasurementRuntime). 시각은 MeasurementRuntime.now()에서 읽어 RunSession에 매개변수로 넘긴다.
 * 화면 입력은 누름 종류마다 다른 연산으로, 조정자의 종료는 endRun으로 받는다(D-36, D-49). 일시정지·재개도 연산을 나눈다.
 * 위치 콜백·1초 시계·워치 요청이 서로 다른 스레드에서 올 수 있어 측정 상태는 lock으로 보호한다.
 */
@Singleton
class RunTracker
@Inject
constructor(
    private val archive: RunArchive,
    private val watchBridge: RunWatchBridge,
    private val coach: RunCoach,
    private val runtime: MeasurementRuntime
) {
    private val lock = Any()
    private var session: RunSession? = null
    private var locationFilter = LocationFilter()

    /** 마지막으로 끝난 러닝(늦게 온 심박을 이 기록에 덧붙인다, FR-043). lock 안에서만 쓴다. */
    private var lastFinished: FinishedWindow? = null

    /** 자동 종료 예고(55분)를 낸 정지(그 정지 앞에 닫힌 정지 수). 정지마다 한 번만 낸다. lock 안에서만 쓴다. */
    private var warnedPauseIndex: Int? = null

    /** 워치 시작 화면에 보일 오늘 계획(publishWatchHome). 러닝이 끝난 뒤 시작 화면 표시에도 쓴다. */
    @Volatile
    private var homePlan: PlanSnapshot? = null

    private val _progress = MutableStateFlow(RunProgress.INITIAL)
    val progress: StateFlow<RunProgress> = _progress.asStateFlow()

    private val _consentRequired = MutableStateFlow(false)
    val consentRequired: StateFlow<Boolean> = _consentRequired.asStateFlow()

    private val _watchStartRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val watchStartRequests: SharedFlow<Unit> = _watchStartRequests.asSharedFlow()

    private val _ended = MutableSharedFlow<FinishOutcome>(extraBufferCapacity = 1)

    /**
     * 측정이 끝난 결과(Saved·NotSaved). 화면 길게 누름·워치 종료·정지 60분 자동 종료·조정자 종료·자유런 취소(NotSaved) 모두 보낸다
     * (잠금 중 거부는 보내지 않는다).
     * 러닝 화면이 자기가 누르지 않은 종료(워치·자동 종료, FR-195)도 결과·홈으로 가게 구독한다. 지난 값은 다시 보내지 않는다(A3a 결정).
     * 구독자가 없을 때 끝난 자유런은 [takeUnclaimedEnd]로 한 번 꺼낼 수 있다(A4).
     */
    val ended: SharedFlow<FinishOutcome> = _ended.asSharedFlow()

    /** 러닝 화면이 떨어져 있던 동안(ended 구독자 0) 저장된 자유런 결과와 끝난 시각. lock 안에서만 쓴다. */
    private var unclaimedEnd: Pair<FinishOutcome.Saved, Instant>? = null

    /**
     * 러닝 화면이 떨어져 있던 동안 워치·화면·정지 자동 종료로 저장된 자유런 결과를 한 번 꺼낸다(꺼내면 지운다, A4). 러닝 화면이 다시 붙을
     * 때 ended를 구독한 뒤 불러 결과로 간다. 대결·크루런 조정자 종료(endRun)와 저장하지 않은 종료(거리 0)는 남기지 않고, 새 측정을
     * 시작하거나 끝난 지 [UNCLAIMED_TTL]이 지나면 버린다(오래된 결과로 갑자기 옮기지 않게).
     */
    fun takeUnclaimedEnd(): FinishOutcome? = synchronized(lock) {
        val (outcome, endedAt) = unclaimedEnd ?: return null
        unclaimedEnd = null
        outcome.takeIf { Duration.between(endedAt, runtime.now()) <= UNCLAIMED_TTL }
    }

    /** 워치 요청을 받는 내부 리스너(공개 연산이 아니다, D-95). */
    private val watchRequests =
        object : WatchRequestListener {
            override fun onStartRequested() = onWatchStartRequest()

            override fun onEndRequested() = onWatchEndRequest()

            override fun onPauseRequested() {
                changePause(GuidanceMessage.PAUSED) { active, now -> active.pauseFromWatch(now) }
            }

            override fun onResumeRequested() {
                changePause(GuidanceMessage.RESUMED) { active, now -> active.resumeFromWatch(now) }
            }

            override fun onHeartRates(samples: List<HeartRateSample>) = acceptHeartRates(samples)
        }

    init {
        watchBridge.bind(watchRequests)
    }

    /** 카운트다운 전 확인: OS 위치 권한과 서버 위치 동의(FR-031, NFR-022). 없으면 consentRequired를 참으로 둔다. */
    fun checkStartable(): Outcome<Unit> {
        val startable = runtime.checkStartable()
        _consentRequired.value = startable is Outcome.Failure
        return startable
    }

    /**
     * 측정을 시작한다. 포그라운드 측정 서비스를 띄우지 못하면(OS 거부) 만든 세션을 되돌리고 그 실패를 돌려준다:
     * 서비스 없이 측정 중으로 남으면 화면을 끄는 순간 위치가 끊긴다. 근접 알림 전송 기록은 회마다 비운다(D-158 (3)).
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
                    fresh.start(runtime.now())
                    session = fresh
                    unclaimedEnd = null
                    locationFilter = LocationFilter()
                    warnedPauseIndex = null
                    coach.begin(spec.mode)
                    _progress.value = RunProgress.INITIAL
                }
            }
        watchBridge.resetProximity()
        watchBridge.publishNotice(null)
        val hosted = runtime.startForeground()
        if (hosted is Outcome.Failure) {
            synchronized(lock) { if (session === created) session = null }
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

    /**
     * 1초 진행: 진행값 방출 → 안내(정지 중이면 평가하지 않는다, FR-049) → 워치 표시. 정지 중이면 정지 한도를 본다(FR-195):
     * 지금 정지가 55분에 처음 닿으면 예고 한 번, 60분에 닿으면 endRun과 같은 저장 규칙으로 끝낸다(D-93 (6), D-157 (7)).
     */
    fun tick(now: Instant) {
        val (active, progress, pause) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return
                // 잠금 연산의 isLocked 갱신과 순서가 뒤바뀌지 않도록 방출은 락 안에서 한다.
                Triple(active, active.tick(now).also { _progress.value = it }, active.currentPause(now))
            }
        when {
            pause == null -> coach.guide(progress, active)
            pause >= PauseLimit.END_AFTER -> return endAfterPauseLimit(active, now)
            pause >= PauseLimit.WARN_AFTER -> warnPauseLimitOnce(active)
        }
        publishWhileActive(active, progress)
    }

    /**
     * 그 세션이 아직 측정 중일 때만 워치 표시를 보낸다. 확인과 보내기를 lock 한 번에 해서, 이 tick이 진행값을 만든 뒤 다른 스레드가
     * 끝내고 시작 화면을 보냈다면 늦은 달리는 중 표시가 그 뒤에 덮지 않는다(AN5-C-7). 보내기는 큐에 넣기만 해 lock을 오래 잡지 않는다.
     */
    private fun publishWhileActive(active: RunSession, progress: RunProgress) {
        synchronized(lock) {
            if (session === active && active.isActive) watchBridge.publish(progress.toWatchDisplay(active.spec))
        }
    }

    fun lockScreen() {
        synchronized(lock) {
            if (session?.tryLock() == true) _progress.update { it.copy(isLocked = true) }
        }
    }

    fun onUnlockTapped() {
        val hint = synchronized(lock) { session?.unlockHintIfLocked() }
        hint?.let(coach::play)
    }

    fun onUnlockLongPressed() {
        synchronized(lock) {
            if (session?.tryUnlock() == true) _progress.update { it.copy(isLocked = false) }
        }
    }

    /** 폰 일시정지 버튼(FR-194). 자유런 RUNNING일 때만 정지한다. 잠금 중의 폰 버튼은 받지 않는다(RunSession.pause가 거짓). */
    fun onPauseTapped() {
        changePause(GuidanceMessage.PAUSED) { active, now -> active.pause(now) }
    }

    /** 폰 재개 버튼(FR-194). PAUSED일 때만 재개한다(PAUSED_LOCKED의 폰 버튼은 받지 않는다, D-120). */
    fun onResumeTapped() {
        changePause(GuidanceMessage.RESUMED) { active, now -> active.resume(now) }
    }

    fun onFinishTapped() {
        val hint = synchronized(lock) { session?.takeIf { it.isActive }?.hintFinish() }
        hint?.let(coach::play)
    }

    /**
     * 화면의 종료(길게 누름). 잠금 중(PAUSED_LOCKED 포함)이면 거부하고 해제 안내를 낸다(FR-181, FR-182, D-157 (3)).
     * 정지 중에도 끝낼 수 있다(FR-036). 측정 중인 세션이 없으면 NotSaved이고 아무것도 부르지 않는다.
     */
    fun onFinishLongPressed(): FinishOutcome {
        val now = runtime.now()
        var isFree = false
        val (outcome, hint) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return FinishOutcome.NotSaved
                isFree = active.spec.mode == RunMode.FREE
                val outcome = active.finish(now)
                if (outcome != FinishOutcome.Rejected) rememberFinished(active, now)
                outcome to if (outcome == FinishOutcome.Rejected) active.hintUnlock() else null
            }
        if (hint != null) {
            coach.play(hint)
        } else {
            complete(outcome, endNotice = null, isClaimable = isFree)
        }
        return outcome
    }

    /** 조정자의 종료(대결 종료 신호·도착·그만두기). 화면 연산을 부르지 않고 잠금과 무관하게 확정한다. */
    fun endRun(): FinishOutcome {
        val outcome = endActive(runtime.now()) ?: return FinishOutcome.NotSaved
        complete(outcome, endNotice = null)
        return outcome
    }

    /**
     * 화면의 취소(자유런, 기록을 남기지 않는 종료). 잠금 중·대결·크루런이거나 측정 중이 아니면 거짓이고 아무것도 하지 않는다.
     * 취소하면 측정 서비스를 멈추고 워치를 시작 화면으로 돌린 뒤(워치 측정도 끝난다) ended로 NotSaved를 보낸다. 보관(RunArchive),
     * 남긴 결과(takeUnclaimedEnd), 늦은 심박 창(lastFinished) 어디에도 남기지 않아 기록·집계에 들어가지 않는다.
     */
    fun discardRun(): Boolean {
        val isDiscarded = synchronized(lock) { session?.discard() == true }
        if (isDiscarded) complete(FinishOutcome.NotSaved, endNotice = null)
        return isDiscarded
    }

    /**
     * 대결 격차(FR-189, FR-196). 값이 있으면 RunSession에 넘기고, 근접 알림을 판정해 워치로 보낸다(A-23).
     * null은 상대 연결 확인 중이다: 격차는 그대로 두고 근접 알림을 바로 멈춘다(D-145 (1)).
     */
    fun onGapChanged(meters: Int?) {
        synchronized(lock) {
            val active = session?.takeIf { it.isActive } ?: return
            if (meters != null) active.updateGap(meters)
        }
        val alert = coach.proximityOf(meters, watchBridge.isWatchConnected())
        watchBridge.publishProximity(alert, runtime.now())
    }

    fun onRankChanged(rank: CrewRank) {
        synchronized(lock) { session?.takeIf { it.isActive }?.updateRank(rank) }
    }

    /**
     * 대결 추월·역전 안내(FR-188). 화면에는 늘 보이고 진동은 대결 진동 설정이 켜져 있을 때만 붙으며 음성은 없다(FR-109, TF-AN-03).
     * 워치 1초 긴 진동은 근접 알림 설정만 따르고 대결 진동 설정과 무관하다(D-145 (2)).
     */
    fun onLeadChanged(kind: Guidance.Kind, message: GuidanceMessage, args: List<Any> = emptyList()) {
        coach.play(Guidance(kind, message, args, hasVoice = false, hasVibration = coach.isBattleVibrationOn()))
        if (coach.isProximityAlertOn()) watchBridge.publishLeadChange(kind)
    }

    /**
     * 워치 시작 화면 표시(running = false, 오늘 계획, FR-042). WatchHomeSync가 앱 시작 때와 목표가 바뀔 때마다 부른다.
     * 측정 중이면 계획만 기억했다가 측정이 끝난 뒤 시작 화면에 보인다.
     */
    fun publishWatchHome(plan: PlanSnapshot?) {
        homePlan = plan
        val measuring = synchronized(lock) { session?.isActive == true }
        if (!measuring) watchBridge.publishHome(plan)
    }

    /** 측정이 끝난 뒤 늦게 온 심박을 보관 기록에 덧붙인다(FR-043). 재시도·다음 묶음과 합치기는 RunArchive가 한다. */
    private fun onLateHeartRates(recordId: String, samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        archive.appendLateHeartRates(recordId, samples)
    }

    /** 정지·재개 공통: 바뀌었으면 PAUSE 안내와 워치 표시를 보낸다. 재개면 정지 한도 안내를 지운다. 맞지 않는 요청은 무시한다. */
    private fun changePause(message: GuidanceMessage, change: (RunSession, Instant) -> Boolean) {
        val now = runtime.now()
        val (active, progress) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return
                if (!change(active, now)) return
                active to active.tick(now).also { _progress.value = it }
            }
        coach.play(Guidance.screenOnly(Guidance.Kind.PAUSE, message))
        if (!progress.isPaused) watchBridge.publishNotice(null)
        // 그 사이 다른 스레드가 끝내고 시작 화면을 보냈으면 늦은 표시로 덮지 않는다(AN5-C-7).
        publishWhileActive(active, progress)
    }

    /** 지금 정지에 예고를 아직 내지 않았으면 낸다(정지 구간마다 한 번). 워치 안내는 재개·종료까지 남는다. */
    private fun warnPauseLimitOnce(active: RunSession) {
        val isFirst =
            synchronized(lock) {
                val index = active.pauses.size
                // 락 밖에서 재개·종료됐으면 예고하지 않는다(재개가 이미 안내를 지웠다).
                (active.isPaused && warnedPauseIndex != index).also { if (it) warnedPauseIndex = index }
            }
        if (!isFirst) return
        coach.play(Guidance.screenOnly(Guidance.Kind.PAUSE, GuidanceMessage.PAUSE_AUTO_END_SOON))
        watchBridge.publishNotice(WatchNotice.AUTO_END_SOON)
    }

    /**
     * 정지 60분 자동 종료: endRun과 같은 저장 규칙(거리 0이면 저장하지 않음, FR-036)으로 끝내고 안내한다. tick이 한도를 본 뒤 lock 밖에서
     * 재개·종료가 끼었을 수 있으므로 끝내기 전에 lock 안에서 그 세션이 아직 같은 정지로 한도를 넘겼는지 다시 본다(AN5-C-7).
     */
    private fun endAfterPauseLimit(active: RunSession, now: Instant) {
        val outcome = endIfStillPaused(active, now) ?: return
        complete(outcome, endNotice = WatchNotice.AUTO_ENDED, isClaimable = active.spec.mode == RunMode.FREE)
        coach.play(Guidance.screenOnly(Guidance.Kind.PAUSE, GuidanceMessage.PAUSE_AUTO_ENDED))
        watchBridge.publishNotice(null)
    }

    /** [active]가 아직 측정 중이고 지금 정지가 한도를 넘겼을 때만 확정한다. 아니면 null. */
    private fun endIfStillPaused(active: RunSession, now: Instant): FinishOutcome? = synchronized(lock) {
        val pause = active.currentPause(now)
        val isStillOverLimit = session === active && active.isActive && pause != null && pause >= PauseLimit.END_AFTER
        if (isStillOverLimit) active.end(now).also { rememberFinished(active, now) } else null
    }

    /** 측정 중인 세션을 잠금과 무관하게 확정한다. 없으면 null. */
    private fun endActive(now: Instant): FinishOutcome? = synchronized(lock) {
        val active = session?.takeIf { it.isActive } ?: return null
        active.end(now).also { rememberFinished(active, now) }
    }

    /** 방금 끝난 세션의 시간 창을 기억한다. lock 안에서만 부른다. */
    private fun rememberFinished(finished: RunSession, endedAt: Instant) {
        val start = finished.startedAt ?: return
        lastFinished = FinishedWindow(finished.spec.recordId, start, endedAt)
    }

    /**
     * 측정 서비스를 멈추고 워치를 시작 화면으로 돌린 뒤(S-18), 저장할 기록이면 보관한다. 실패하면 화면 안내로 알린다.
     * [isClaimable](자유런 종료)이고 러닝 화면이 구독하지 않으면 결과를 남겨 화면이 다시 붙을 때 꺼내게 한다(takeUnclaimedEnd).
     */
    private fun complete(outcome: FinishOutcome, endNotice: WatchNotice?, isClaimable: Boolean = false) {
        runtime.stop()
        // 구독자 확인·보관·방출을 한 lock 안에서 한다: 그 사이 화면이 구독하면 방출로 받고, 아니면 takeUnclaimedEnd로 꺼낸다.
        synchronized(lock) {
            if (isClaimable && outcome is FinishOutcome.Saved && _ended.subscriptionCount.value == 0) {
                unclaimedEnd = outcome to runtime.now()
            }
            _ended.tryEmit(outcome)
        }
        watchBridge.publishNotice(endNotice)
        watchBridge.publishHome(homePlan)
        if (outcome is FinishOutcome.Saved) {
            archive.saveInBackground(outcome.run) {
                coach.play(Guidance.screenOnly(Guidance.Kind.SAVE_FAILED, GuidanceMessage.SAVE_FAILED))
            }
        }
    }

    private fun acceptHeartRates(samples: List<HeartRateSample>) {
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
        val active = synchronized(lock) { session?.takeIf { it.isActive } }
        val notice =
            when {
                active != null -> WatchNotice.ALREADY_MEASURING
                _watchStartRequests.subscriptionCount.value == 0 -> WatchNotice.OPEN_RUN_SCREEN
                else -> null
            }
        if (notice == null) {
            _watchStartRequests.tryEmit(Unit)
            return
        }
        watchBridge.publish(
            _progress.value.toWatchDisplay(active?.spec, notice = notice, isRunning = active != null)
        )
    }

    /**
     * 워치 종료 요청(워치에서 길게 누름, FR-181). 자유런이면 화면의 길게 누르기와 같은 규칙(잠금 중 거부)으로 끝낸다.
     * 대결·크루런은 서버 보고(그만두기)를 거쳐야 해서 폰에서만 끝내고 워치에 안내한다.
     */
    private fun onWatchEndRequest() {
        val spec = synchronized(lock) { session?.takeIf { it.isActive }?.spec } ?: return
        if (spec.mode == RunMode.FREE) {
            onFinishLongPressed()
        } else {
            watchBridge.publish(_progress.value.toWatchDisplay(spec, notice = WatchNotice.END_ON_PHONE))
        }
    }

    /** 측정 중 워치 표시. isPausable은 spec.mode.isPausable 한 곳에서 읽는다(D-137). */
    private fun RunProgress.toWatchDisplay(
        spec: RunStartSpec?,
        notice: WatchNotice? = null,
        isRunning: Boolean = true
    ): WatchDisplay = WatchDisplay(
        isRunning = isRunning,
        elapsed = elapsed,
        distance = distance,
        currentPace = currentPace,
        heartRateBpm = heartRate?.bpm,
        todayPlan = spec?.todayPlan,
        notice = notice,
        isPaused = isPaused,
        isPausable = spec?.mode?.isPausable == true
    )

    private companion object {
        /** 남긴 종료 결과를 꺼낼 수 있는 시간. 지나면 다시 붙은 러닝 화면이 결과로 옮기지 않는다. */
        val UNCLAIMED_TTL: Duration = Duration.ofMinutes(10)
    }

    private data class FinishedWindow(val recordId: String, val startedAt: Instant, val endedAt: Instant) {
        fun contains(at: Instant): Boolean = !at.isBefore(startedAt) && !at.isAfter(endedAt)
    }
}
