package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RouteLine
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

/**
 * 한 번의 러닝 측정 흐름을 조정한다(6.1.14, D-56). 측정 상태를 가지므로 ui.run, data.device.MeasurementService,
 * 두 조정자가 같은 인스턴스를 받는다(@Singleton, design-review.md M49).
 *
 * 생성자 의존은 책임이 한 문장인 협력 객체 넷이다(D-95): 보관(RunArchive), 워치(RunWatchBridge), 안내(RunCoach),
 * 실행 환경(MeasurementRuntime). 시각은 MeasurementRuntime.now()에서 읽어 RunSession에 매개변수로 넘긴다.
 * 화면 입력은 누름 종류마다 다른 연산으로, 조정자의 종료는 endRun으로 받는다(D-36, D-49). 일시정지·재개는 폰 버튼·워치 요청이
 * 같은 RunSession.pause·resume으로 모인다(D-256). 앱 화면 잠금과 정지 자동 종료는 없다(FR-182·FR-195 삭제).
 * 잠금 화면 진행 알림 값([notice], D-258)과 측정 중 경로선([routeLine], D-261)도 여기서 낸다.
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
    private var stabilizedLocationFilter = LocationFilter(isStabilizationEnabled = true)

    /** 마지막으로 끝난 러닝(늦게 온 심박을 이 기록에 덧붙인다, FR-043). lock 안에서만 쓴다. */
    private var lastFinished: FinishedWindow? = null

    /** 워치 시작 화면에 보일 오늘 계획(publishWatchHome). 러닝이 끝난 뒤 시작 화면 표시에도 쓴다. */
    @Volatile
    private var homePlan: PlanSnapshot? = null

    private val _progress = MutableStateFlow(RunProgress.INITIAL)
    val progress: StateFlow<RunProgress> = _progress.asStateFlow()

    private val _notice = MutableStateFlow<ProgressNotice?>(null)

    /**
     * 잠금 화면 진행 알림 값(D-258). 측정을 시작하면 첫 값을 내고 1초 tick·정지·재개마다 갱신하며, 측정이 끝나거나 취소되면 null이다.
     * data.device.MeasurementService가 구독해 같은 알림 ID로 다시 그린다.
     */
    val notice: StateFlow<ProgressNotice?> = _notice.asStateFlow()

    private val _routeLine = MutableStateFlow(RouteLine.EMPTY)

    /** 측정 중 경로선(D-261). 거른 위치가 경로에 남을 때마다 점이 하나 는다. 측정이 끝나거나 새로 시작하면 비운다. */
    val routeLine: StateFlow<RouteLine> = _routeLine.asStateFlow()

    /** 진행 알림의 대결 격차. 연결 확인 중(null)이면 비운다(RunSession은 마지막 격차를 안내용으로 남긴다). */
    @Volatile
    private var noticeGap: Int? = null

    private val _consentRequired = MutableStateFlow(false)
    val consentRequired: StateFlow<Boolean> = _consentRequired.asStateFlow()

    private val _watchStartRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val watchStartRequests: SharedFlow<Unit> = _watchStartRequests.asSharedFlow()

    private val _ended = MutableSharedFlow<FinishOutcome>(extraBufferCapacity = 1)

    /**
     * 측정이 끝난 결과(Saved·NotSaved). 화면 길게 누름·워치 종료·조정자 종료·자유런 취소(NotSaved) 모두 보낸다.
     * 러닝 화면이 자기가 누르지 않은 종료(워치)도 결과·홈으로 가게 구독한다. 지난 값은 다시 보내지 않는다(A3a 결정).
     * 구독자가 없을 때 끝난 자유런은 [takeUnclaimedEnd]로 한 번 꺼낼 수 있다(A4).
     */
    val ended: SharedFlow<FinishOutcome> = _ended.asSharedFlow()

    /** 러닝 화면이 떨어져 있던 동안(ended 구독자 0) 저장된 자유런 결과와 끝난 시각. lock 안에서만 쓴다. */
    private var unclaimedEnd: Pair<FinishOutcome.Saved, Instant>? = null

    /**
     * 러닝 화면이 떨어져 있던 동안 워치·화면 종료로 저장된 자유런 결과를 한 번 꺼낸다(꺼내면 지운다, A4). 러닝 화면이 다시 붙을
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

            override fun onPauseRequested() = pause()

            override fun onResumeRequested() = resume()

            override fun onHeartRates(samples: List<HeartRateSample>) = acceptHeartRates(samples)

            override fun onWatchMotions(samples: List<MotionSample>) {
                synchronized(lock) {
                    val active = session?.takeIf { it.isActive } ?: return
                    samples.forEach { active.onMotion(it, isFromWatch = true) }
                }
            }
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
     * 짝지은 워치가 스스로 측정 중이면 Rejected(WATCH_MEASURING)로 거부한다(FR-252, D-373 (2)): 위치 동의 확인보다 먼저 봐서
     * 동의 안내로 잘못 보내지 않는다.
     */
    fun startRun(spec: RunStartSpec): Outcome<Unit> {
        if (isWatchMeasuring) return Outcome.Failure(AppError.Rejected(AppError.Rejected.WATCH_MEASURING))
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
                    stabilizedLocationFilter = LocationFilter(
                        isStabilizationEnabled = true,
                        isWatch = fresh.spec.startDevice == RunStartSpec.StartDevice.WATCH
                    )
                    coach.begin(spec.mode)
                    noticeGap = null
                    _progress.value = RunProgress.INITIAL
                    _routeLine.value = RouteLine.EMPTY
                    _notice.value = ProgressNotice.of(spec, RunProgress.INITIAL, gapMeters = null)
                }
            }
        watchBridge.resetProximity()
        val hosted = runtime.startForeground()
        if (hosted is Outcome.Failure) {
            synchronized(lock) {
                if (session === created) {
                    session = null
                    _notice.value = null
                }
            }
            return hosted
        }
        return Outcome.Success(Unit)
    }

    fun onPhoneLocationSample(s: LocationSample) = onLocationSample(s, stabilize = true)

    fun onLocationSample(s: LocationSample, stabilize: Boolean = false) {
        synchronized(lock) {
            val active = session?.takeIf { it.isActive } ?: return
            val isFreeRun = active.spec.mode == RunMode.FREE && !active.spec.isOfflineBattle
            val filter = if (stabilize && isFreeRun) {
                if (active.isPaused) return
                stabilizedLocationFilter
            } else {
                locationFilter
            }
            val points = if (isFreeRun && stabilize && active.spec.startDevice == RunStartSpec.StartDevice.WATCH) {
                filter.acceptBatch(s)
            } else {
                listOfNotNull(filter.accept(s))
            }
            for (point in points) {
                if (active.onLocation(point)) _routeLine.value = _routeLine.value + point.point
            }
        }
    }

    /** 기기가 잰 속도·케이던스(S15P31A207-242). 측정 중일 때만 세션에 남긴다. */
    fun onMotionSample(s: MotionSample) {
        synchronized(lock) {
            session?.takeIf { it.isActive }?.onMotion(s)
        }
    }

    /**
     * 1초 진행: 진행값 방출 → 안내(정지 중이면 평가하지 않는다, FR-049) → 워치 표시. 정지 길이에는 상한이 없어 정지 중에는
     * 진행값만 낸다(FR-195 삭제, D-257).
     */
    fun tick(now: Instant) {
        val (active, progress) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return
                // 정지·재개의 진행값 방출과 순서가 뒤바뀌지 않도록 방출은 락 안에서 한다.
                active to active.tick(now).also(::emitProgress)
            }
        if (!progress.isPaused) coach.guide(progress, active)
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

    /** 폰 일시정지 버튼(FR-194). 자유런·크루런 RUNNING일 때만 정지한다. */
    fun onPauseTapped() = pause()

    /** 폰 재개 버튼(FR-194). PAUSED일 때만 재개한다. */
    fun onResumeTapped() = resume()

    /** 진행 알림의 "일시정지" 버튼(FR-224, D-260). 폰 버튼과 같은 길이고 자유런·크루런 RUNNING일 때만 정지한다. */
    fun onNotificationPauseTapped() = pause()

    /** 진행 알림의 "재개" 버튼(FR-224, D-260). PAUSED일 때만 재개한다. */
    fun onNotificationResumeTapped() = resume()

    fun onFinishTapped() {
        val hint = synchronized(lock) { session?.takeIf { it.isActive }?.hintFinish() }
        hint?.let(coach::play)
    }

    fun measurementTime(): Instant = runtime.now()

    /**
     * 화면의 종료(길게 누름, FR-181). 정지 중에도 끝낼 수 있다(FR-036). 측정 중인 세션이 없으면 NotSaved이고 아무것도 부르지 않는다.
     */
    fun onFinishLongPressed(now: Instant = runtime.now()): FinishOutcome {
        var isFree = false
        val outcome =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return FinishOutcome.NotSaved
                isFree = active.spec.mode == RunMode.FREE
                active.finish(now).also { rememberFinished(active, now) }
            }
        complete(outcome, isClaimable = isFree)
        return outcome
    }

    /** 조정자의 종료(대결 종료 신호·도착·그만두기). 화면 연산을 부르지 않는다. */
    fun endRun(): FinishOutcome {
        val outcome = endActive(runtime.now()) ?: return FinishOutcome.NotSaved
        complete(outcome)
        return outcome
    }

    /**
     * 짝지은 워치가 스스로 러닝을 측정 중인가(FR-252, D-373 (2)). 참이면 [startRun]이 거부하고, 매칭 요청 전 확인
     * (BattleRunCoordinator.requestMatch)도 이 값을 읽는다.
     */
    val isWatchMeasuring: Boolean
        get() = watchBridge.isWatchMeasuring()

    /** 측정 중인가(정지 포함). 친구 대결 참가 전 확인(BattleRunCoordinator.isBusy, D-273)이 읽는다. */
    val isMeasuring: Boolean
        get() = synchronized(lock) { session?.isActive == true }

    /**
     * 기록을 남기지 않는 종료(자유런 취소, 셀프런·셀럽런 그만두기, D-266). 배틀런·크루런이거나 측정 중이 아니면 거짓이고 아무것도 하지 않는다.
     * 취소하면 측정 서비스를 멈추고 워치를 시작 화면으로 돌린 뒤(워치 측정도 끝난다) ended로 NotSaved를 보낸다. 보관(RunArchive),
     * 남긴 결과(takeUnclaimedEnd), 늦은 심박 창(lastFinished) 어디에도 남기지 않아 기록·집계에 들어가지 않는다.
     */
    fun discardRun(): Boolean {
        val isDiscarded = synchronized(lock) { session?.discard() == true }
        if (isDiscarded) complete(FinishOutcome.NotSaved)
        return isDiscarded
    }

    /**
     * 대결 격차(FR-189, FR-196). 값이 있으면 RunSession에 넘기고, 근접 알림을 판정해 워치로 보낸다(A-23).
     * null은 상대 연결 확인 중이다: 격차는 그대로 두고 근접 알림을 바로 멈춘다(D-145 (1)).
     */
    fun onGapChanged(meters: Int?) {
        synchronized(lock) {
            val active = session?.takeIf { it.isActive } ?: return
            noticeGap = meters
            if (meters != null) active.updateGap(meters)
        }
        val alert = coach.proximityOf(meters, watchBridge.isWatchConnected())
        watchBridge.publishProximity(alert, runtime.now())
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

    /** 일시정지(FR-194). 폰 버튼·워치 요청이 모두 이 길로 온다(D-256). */
    private fun pause() {
        changePause(GuidanceMessage.PAUSED) { active, now -> active.pause(now) }
    }

    /** 재개(FR-194). 폰 버튼·워치 요청이 모두 이 길로 온다. */
    private fun resume() {
        changePause(GuidanceMessage.RESUMED) { active, now -> active.resume(now) }
    }

    /** 정지·재개 공통: 바뀌었으면 PAUSE 안내와 워치 표시를 보낸다. 맞지 않는 요청은 무시한다(멱등). */
    private fun changePause(message: GuidanceMessage, change: (RunSession, Instant) -> Boolean) {
        val now = runtime.now()
        val (active, progress) =
            synchronized(lock) {
                val active = session?.takeIf { it.isActive } ?: return
                if (!change(active, now)) return
                if (active.spec.mode == RunMode.FREE) {
                    stabilizedLocationFilter = LocationFilter(
                        isStabilizationEnabled = true,
                        isWatch = active.spec.startDevice == RunStartSpec.StartDevice.WATCH
                    )
                }
                active to active.tick(now).also(::emitProgress)
            }
        coach.play(Guidance.screenOnly(Guidance.Kind.PAUSE, message))
        // 그 사이 다른 스레드가 끝내고 시작 화면을 보냈으면 늦은 표시로 덮지 않는다(AN5-C-7).
        publishWhileActive(active, progress)
    }

    /** 진행값과 알림 값을 함께 낸다. lock 안에서만 부른다. */
    private fun emitProgress(progress: RunProgress) {
        _progress.value = progress
        session?.let { _notice.value = ProgressNotice.of(it.spec, progress, noticeGap) }
    }

    /** 측정 중인 세션을 확정한다. 없으면 null. */
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
    private fun complete(outcome: FinishOutcome, isClaimable: Boolean = false) {
        runtime.stop()
        // 구독자 확인·보관·방출을 한 lock 안에서 한다: 그 사이 화면이 구독하면 방출로 받고, 아니면 takeUnclaimedEnd로 꺼낸다.
        synchronized(lock) {
            _notice.value = null
            // 끝난 측정의 경로는 화면이 더 쓰지 않는다(결과는 보관·서버 기록으로 본다). 싱글턴에 수천 점을 남기지 않는다.
            _routeLine.value = RouteLine.EMPTY
            if (isClaimable && outcome is FinishOutcome.Saved && _ended.subscriptionCount.value == 0) {
                unclaimedEnd = outcome to runtime.now()
            }
            _ended.tryEmit(outcome)
        }
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
     * 워치 종료 요청(워치에서 길게 누름, FR-181). 자유런이면 화면의 길게 누르기와 같은 규칙으로 끝낸다.
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
