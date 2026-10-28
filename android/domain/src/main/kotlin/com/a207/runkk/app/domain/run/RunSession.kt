package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 한 번의 러닝 측정 상태를 보관한다(6.1.14, A-02). 시각은 모두 매개변수로 받는다.
 *
 * 상태 전이(상태도 2.4.7 v0.11): COUNTDOWN → start → RUNNING ⇄ (pause / resume) PAUSED, RUNNING ⇄ (lock / unlock) LOCKED,
 * LOCKED ⇄ PAUSED_LOCKED(워치 정지·재개), PAUSED → PAUSED_LOCKED(lock), PAUSED_LOCKED → PAUSED(unlock),
 * 측정 중 네 상태 → finish·end → FINISHED, 자유런 RUNNING·PAUSED → discard(취소, 기록 없음) → FINISHED.
 * hintFinish·hintUnlock은 상태를 바꾸지 않는다(D-49).
 * 폰 버튼(pause·resume)은 RUNNING ⇄ PAUSED만, 워치 요청(pauseFromWatch·resumeFromWatch)은 잠금 중에도 받는다(D-120, D-157).
 * "정지 중"은 [isPaused](PAUSED 또는 PAUSED_LOCKED) 하나로 판정한다(D-157 (2)).
 */
class RunSession(val spec: RunStartSpec) {
    enum class State {
        COUNTDOWN,
        RUNNING,
        PAUSED,
        LOCKED,
        PAUSED_LOCKED,
        FINISHED
    }

    var state: State = State.COUNTDOWN
        private set

    /** 대결 격차(m, 앞서면 양수). GapGuidanceRule 입력. RunCoach가 RunTracker의 락 밖에서 읽는다. */
    @Volatile
    var gapMeters: Int? = null
        private set

    /** 크루런 순위. RankGuidanceRule 입력. RunCoach가 RunTracker의 락 밖에서 읽는다. */
    @Volatile
    var rank: CrewRank? = null
        private set

    /** 측정 시작 일시. 시작 전이면 null. */
    var startedAt: Instant? = null
        private set

    /** 마지막 재개 시각(D-121). 재개 뒤 첫 포인트가 불안정이면 불안정 구간이 여기서 시작한다. */
    var lastResumedAt: Instant? = null
        private set

    private val closedPauses = mutableListOf<PauseInterval>()

    /** 닫힌 정지 구간(시간순). */
    val pauses: List<PauseInterval>
        get() = closedPauses.toList()

    /** 열린 정지 구간의 시작 시각. 정지 중(PAUSED·PAUSED_LOCKED)일 때만 값이 있다. */
    private var pausedSince: Instant? = null

    private var distanceMeters = 0.0
    private val points = mutableListOf<RoutePoint>()
    private val unstable = mutableListOf<UnstableSegment>()
    private val heartRates = mutableListOf<HeartRateSample>()

    /** 포인트마다 (일시, 그때까지 누적 거리). 도달 시각 계산에 쓴다. */
    private val arrivals = mutableListOf<Pair<Instant, Double>>()

    /** 최근 30초 창의 (일시, 누적 거리). 현재 페이스 계산에 쓴다(D-14). */
    private val window = ArrayDeque<Pair<Instant, Double>>()
    private var lastNormal: FilteredPoint? = null
    private var lastSignal: SignalState? = null
    private var lastCurrentPace: Pace? = null

    /** 측정 중(정지·잠금 포함). 시작 전과 종료 뒤는 거짓이다. */
    val isActive: Boolean
        get() = state == State.RUNNING || state == State.LOCKED || isPaused

    /** 정지 중(PAUSED 또는 PAUSED_LOCKED, D-157 (2)). */
    val isPaused: Boolean
        get() = state == State.PAUSED || state == State.PAUSED_LOCKED

    /** 화면 잠금 중(LOCKED 또는 PAUSED_LOCKED). */
    val isLocked: Boolean
        get() = state == State.LOCKED || state == State.PAUSED_LOCKED

    fun start(at: Instant) {
        check(state == State.COUNTDOWN) { "cannot start in state $state" }
        startedAt = at
        state = State.RUNNING
    }

    /**
     * 포인트를 반영한다. 정지 중이면 버린다(D-93 (1)). NORMAL이면 직전 정상 포인트와의 거리를 더하고, UNSTABLE이면 거리를 더하지 않고
     * 불안정 구간을 남긴다(Q-18). 재개 뒤 첫 포인트는 직전 정상 포인트가 없어 거리를 더하지 않는다.
     * 직전 포인트와의 시속이 25km/h를 넘으면(순간이동·차량 이동) 그 구간 거리도 더하지 않는다(FR-047 v0.10, [OverspeedRule]).
     */
    fun onLocation(p: FilteredPoint) {
        if (!isActive || isPaused) return
        points += RoutePoint(p.at, p.point, p.state)
        lastSignal = p.state
        val previous = lastNormal
        if (p.state == SignalState.UNSTABLE) {
            val from = previous?.at ?: lastResumedAt ?: requireNotNull(startedAt)
            val skipped = previous?.point?.distanceTo(p.point) ?: Distance.ZERO
            unstable += UnstableSegment(from, p.at, skipped)
        } else if (previous != null) {
            val meters = previous.point.metersTo(p.point)
            if (!OverspeedRule.isOverspeed(meters, previous.at, p.at)) distanceMeters += meters
        }
        lastNormal = p
        arrivals += p.at to distanceMeters
        window += p.at to distanceMeters
        val windowStart = p.at.minus(CURRENT_PACE_WINDOW)
        while (window.size > 1 && window.first().first.isBefore(windowStart)) {
            window.removeFirst()
        }
    }

    /** 심박을 남긴다. 정지 중에도 남긴다(D-93 (1)). */
    fun onHeartRate(s: HeartRateSample) {
        if (!isActive) return
        heartRates += s
    }

    /**
     * 1초 진행 지표. 경과는 운동 시간(벽시계 경과 − 정지 합)이고 평균 페이스 = 운동 시간 ÷ 거리다(FR-034).
     * 정지 중에는 거리·운동 시간이 늘지 않고 현재 페이스는 빈 값, 심박은 마지막으로 받은 값이다(D-144, D-157 (4)).
     */
    fun tick(now: Instant): RunProgress {
        val start = startedAt ?: return RunProgress.INITIAL
        val elapsed = Duration.between(start, now).minus(pausedTotal(now)).coerceAtLeast(Duration.ZERO)
        val distance = Distance(distanceMeters.roundToLong())
        val average = if (distance.meters > 0 && !elapsed.isZero) Pace.of(elapsed, distance) else null
        if (isPaused) {
            return RunProgress(
                elapsed = elapsed,
                distance = distance,
                currentPace = null,
                averagePace = average,
                signal = lastSignal,
                isLocked = isLocked,
                heartRate = heartRates.lastOrNull(),
                isPaused = true
            )
        }
        currentPace()?.let { lastCurrentPace = it }
        return RunProgress(
            elapsed = elapsed,
            distance = distance,
            currentPace = lastCurrentPace,
            averagePace = average,
            signal = lastSignal,
            isLocked = isLocked,
            heartRate = heartRates.lastOrNull()?.takeIf { Duration.between(it.at, now) <= HEART_RATE_FRESHNESS }
        )
    }

    /** 지금 정지 길이. 정지 중이 아니면 null(FR-195 정지 한도 검사). */
    fun currentPause(now: Instant): Duration? = pausedSince?.let { Duration.between(it, maxOf(now, it)) }

    /** 정지 합(닫힌 구간 + 열린 구간). 운동 시간 계산에 쓴다. */
    internal fun pausedTotal(now: Instant): Duration {
        var sum = Duration.ZERO
        for (p in closedPauses) sum = sum.plus(Duration.between(p.from, p.to))
        return sum.plus(currentPause(now) ?: Duration.ZERO)
    }

    /** 폰 일시정지 버튼(FR-194): 자유런 RUNNING → PAUSED. 그 밖의 모드·상태면 거짓이고 상태는 그대로다(D-93 (3), D-137). */
    fun pause(at: Instant): Boolean {
        if (!spec.mode.isPausable) return false
        if (state != State.RUNNING) return false
        pausedSince = at
        state = State.PAUSED
        return true
    }

    /** 폰 재개 버튼(FR-194): PAUSED → RUNNING. 그 밖의 상태(PAUSED_LOCKED 포함)면 거짓이다. */
    fun resume(at: Instant): Boolean {
        if (state != State.PAUSED) return false
        closePause(at)
        state = State.RUNNING
        return true
    }

    /** 워치 일시정지 요청(D-157 (1)): RUNNING → PAUSED, LOCKED → PAUSED_LOCKED(잠금 유지, D-120). [at]은 폰 처리 시각이다. */
    fun pauseFromWatch(at: Instant): Boolean {
        if (!spec.mode.isPausable) return false
        state =
            when (state) {
                State.RUNNING -> State.PAUSED
                State.LOCKED -> State.PAUSED_LOCKED
                else -> return false
            }
        pausedSince = at
        return true
    }

    /** 워치 재개 요청(D-157 (1)): PAUSED → RUNNING, PAUSED_LOCKED → LOCKED. */
    fun resumeFromWatch(at: Instant): Boolean {
        if (!isPaused) return false
        closePause(at)
        state = if (state == State.PAUSED_LOCKED) State.LOCKED else State.RUNNING
        return true
    }

    /** 화면 잠금: RUNNING → LOCKED, PAUSED → PAUSED_LOCKED(D-120). 잠금 중 finish·설정 조작은 거부한다(FR-182). */
    fun lock() {
        state =
            when (state) {
                State.RUNNING -> State.LOCKED
                State.PAUSED -> State.PAUSED_LOCKED
                else -> error("cannot lock in state $state")
            }
    }

    fun hintUnlock(): Guidance = Guidance.screenOnly(Guidance.Kind.UNLOCK_HINT, GuidanceMessage.UNLOCK_HINT)

    /** 잠금 해제(길게 누름): LOCKED → RUNNING, PAUSED_LOCKED → PAUSED(D-120). */
    fun unlock() {
        state =
            when (state) {
                State.LOCKED -> State.RUNNING
                State.PAUSED_LOCKED -> State.PAUSED
                else -> error("cannot unlock in state $state")
            }
    }

    fun hintFinish(): Guidance = Guidance.screenOnly(Guidance.Kind.FINISH_HINT, GuidanceMessage.FINISH_HINT)

    /** 잠글 수 있으면(RUNNING·PAUSED) 잠그고 참. 그 밖의 상태면 아무것도 하지 않고 거짓(RunTracker가 상태 값을 보지 않게, O-4). */
    fun tryLock(): Boolean {
        if (state != State.RUNNING && state != State.PAUSED) return false
        lock()
        return true
    }

    /** 잠금 중이면 해제하고 참. 그 밖의 상태면 거짓(O-4). */
    fun tryUnlock(): Boolean {
        if (!isLocked) return false
        unlock()
        return true
    }

    /** 잠금 중이면 해제 안내, 아니면 null. 상태는 바꾸지 않는다(O-4). */
    fun unlockHintIfLocked(): Guidance? = if (isLocked) hintUnlock() else null

    /** 화면의 종료(길게 누름). 잠금 중(PAUSED_LOCKED 포함)이면 거부한다(FR-182, D-157 (3)). 정지 중에도 끝낼 수 있다(FR-036). */
    fun finish(now: Instant): FinishOutcome {
        check(isActive) { "cannot finish in state $state" }
        if (isLocked) return FinishOutcome.Rejected
        return complete(now)
    }

    /** 조정자·자동 종료. 화면 입력이 아니라 잠금과 무관하게 확정한다(D-36, FR-195). */
    fun end(now: Instant): FinishOutcome {
        check(isActive) { "cannot end in state $state" }
        return complete(now)
    }

    /**
     * 화면의 취소(기록을 남기지 않는 종료). 취소할 수 있는 모드([isDiscardable], 자유런)이고 측정 중이며 잠금 중이 아닐 때만
     * FINISHED로 바꾸고 참이다. 기록(CompletedRun)을 만들지 않는다. 정지 중에도 취소할 수 있고 열린 정지 구간은 버린다.
     */
    fun discard(): Boolean {
        if (!spec.mode.isDiscardable || !isActive || isLocked) return false
        pausedSince = null
        state = State.FINISHED
        return true
    }

    /** 목표 거리에 처음 닿은 시각(정상 포인트 사이 직선 보간). 닿지 않았으면 null. */
    fun arrivalAt(target: Distance): Instant? {
        val goal = target.meters.toDouble()
        var previous: Pair<Instant, Double>? = null
        for (entry in arrivals) {
            if (entry.second >= goal) {
                val prev = previous ?: return entry.first
                val ratio = (goal - prev.second) / (entry.second - prev.second)
                val millis = Duration.between(prev.first, entry.first).toMillis() * ratio
                return prev.first.plusMillis(millis.toLong())
            }
            previous = entry
        }
        return null
    }

    fun updateGap(meters: Int) {
        gapMeters = meters
    }

    fun updateRank(rank: CrewRank) {
        this.rank = rank
    }

    /** 열린 정지 구간을 닫고 재개 처리를 한다: 재개 뒤 첫 포인트는 거리를 더하지 않고, 현재 페이스 창을 비운다(D-93 (1)(2)). */
    private fun closePause(at: Instant) {
        val from = requireNotNull(pausedSince)
        closedPauses += PauseInterval(from, maxOf(at, from))
        pausedSince = null
        lastResumedAt = at
        lastNormal = null
        window.clear()
        lastCurrentPace = null
    }

    /** 종료: 열린 정지 구간은 종료 시각에 닫는다(D-93 (5)). 경과는 운동 시간이다. */
    private fun complete(now: Instant): FinishOutcome {
        // 다른 스레드가 먼저 읽은 시각으로 닫아도 구간이 뒤집히지 않게 한다(from ≤ to).
        pausedSince?.let { closedPauses += PauseInterval(it, maxOf(now, it)) }
        pausedSince = null
        state = State.FINISHED
        val start = requireNotNull(startedAt)
        val distance = Distance(distanceMeters.roundToLong())
        if (distance.meters == 0L) return FinishOutcome.NotSaved
        return FinishOutcome.Saved(
            CompletedRun(
                recordId = spec.recordId,
                mode = spec.mode,
                cardKind = spec.cardKind,
                battleId = spec.battleId,
                crewRunPostId = spec.crewRunPostId,
                startedAt = start,
                elapsed = Duration.between(start, now).minus(pausedTotal(now)).coerceAtLeast(Duration.ZERO),
                distance = distance,
                route = points.toList(),
                unstable = unstable.toList(),
                heartRates = heartRates.toList(),
                pauses = closedPauses.toList(),
                savedAt = now
            )
        )
    }

    /** 최근 30초 창의 처음·끝 누적 거리 차로 현재 페이스를 낸다(D-14). 거리 차가 1m 미만이거나 시각 차가 없으면 null(S-19). */
    private fun currentPace(): Pace? {
        if (window.size < 2) return null
        val (fromAt, fromMeters) = window.first()
        val (toAt, toMeters) = window.last()
        val meters = (toMeters - fromMeters).roundToLong()
        if (meters <= 0 || !toAt.isAfter(fromAt)) return null
        return Pace.of(Duration.between(fromAt, toAt), Distance(meters))
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

    private companion object {
        val CURRENT_PACE_WINDOW: Duration = Duration.ofSeconds(30)
        val HEART_RATE_FRESHNESS: Duration = Duration.ofSeconds(30)
    }
}
