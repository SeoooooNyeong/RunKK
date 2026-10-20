package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 한 번의 러닝 측정 상태를 보관한다(6.1.14, A-02). 시각은 모두 매개변수로 받는다.
 * 상태 전이: COUNTDOWN → start → RUNNING ⇄ (lock / unlock) LOCKED → finish·end → FINISHED.
 * hintFinish·hintUnlock은 상태를 바꾸지 않는다(D-49).
 */
class RunSession(val spec: RunStartSpec) {
    enum class State {
        COUNTDOWN,
        RUNNING,
        LOCKED,
        FINISHED
    }

    var state: State = State.COUNTDOWN
        private set

    /** 대결 격차(m, 앞서면 양수). GapGuidanceRule 입력. */
    var gapMeters: Int? = null
        private set

    /** 크루런 순위. RankGuidanceRule 입력. */
    var rank: CrewRank? = null
        private set

    /** 측정 시작 일시. 시작 전이면 null. */
    var startedAt: Instant? = null
        private set

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

    val isActive: Boolean
        get() = state == State.RUNNING || state == State.LOCKED

    fun start(at: Instant) {
        check(state == State.COUNTDOWN) { "시작할 수 없는 상태: $state" }
        startedAt = at
        state = State.RUNNING
    }

    fun onLocation(p: FilteredPoint) {
        if (!isActive) return
        points += RoutePoint(p.at, p.point, p.state)
        lastSignal = p.state
        val previous = lastNormal
        if (p.state == SignalState.UNSTABLE) {
            val from = previous?.at ?: requireNotNull(startedAt)
            val skipped = previous?.point?.distanceTo(p.point) ?: Distance.ZERO
            unstable += UnstableSegment(from, p.at, skipped)
        } else if (previous != null) {
            distanceMeters += previous.point.metersTo(p.point)
        }
        lastNormal = p
        arrivals += p.at to distanceMeters
        window += p.at to distanceMeters
        val windowStart = p.at.minus(CURRENT_PACE_WINDOW)
        while (window.size > 1 && window.first().first.isBefore(windowStart)) {
            window.removeFirst()
        }
    }

    fun onHeartRate(s: HeartRateSample) {
        if (!isActive) return
        heartRates += s
    }

    fun tick(now: Instant): RunProgress {
        val start = startedAt ?: return RunProgress.INITIAL
        val elapsed = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
        val distance = Distance(distanceMeters.roundToLong())
        currentPace()?.let { lastCurrentPace = it }
        val average =
            if (distance.meters > 0 && !elapsed.isZero) Pace.of(elapsed, distance) else null
        return RunProgress(
            elapsed = elapsed,
            distance = distance,
            currentPace = lastCurrentPace,
            averagePace = average,
            signal = lastSignal,
            locked = state == State.LOCKED,
            heartRate = heartRates.lastOrNull()?.takeIf { Duration.between(it.at, now) <= HEART_RATE_FRESHNESS }
        )
    }

    fun lock() {
        check(state == State.RUNNING) { "잠글 수 없는 상태: $state" }
        state = State.LOCKED
    }

    fun hintUnlock(): Guidance = Guidance.screenOnly(Guidance.Kind.UNLOCK_HINT, "길게 눌러 해제")

    fun unlock() {
        check(state == State.LOCKED) { "해제할 수 없는 상태: $state" }
        state = State.RUNNING
    }

    fun hintFinish(): Guidance = Guidance.screenOnly(Guidance.Kind.FINISH_HINT, "길게 눌러 종료")

    /** 화면의 종료(길게 누름). 잠금 중이면 거부한다(FR-182). */
    fun finish(now: Instant): FinishOutcome {
        check(isActive) { "종료할 수 없는 상태: $state" }
        if (state == State.LOCKED) return FinishOutcome.Rejected
        return complete(now)
    }

    /** 조정자의 종료. 화면 입력이 아니라 잠금과 무관하게 확정한다(D-36). */
    fun end(now: Instant): FinishOutcome {
        check(isActive) { "종료할 수 없는 상태: $state" }
        return complete(now)
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

    private fun complete(now: Instant): FinishOutcome {
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
                elapsed = Duration.between(start, now).coerceAtLeast(Duration.ZERO),
                distance = distance,
                route = points.toList(),
                unstable = unstable.toList(),
                heartRates = heartRates.toList(),
                savedAt = now
            )
        )
    }

    /** 최근 30초 창의 처음·끝 누적 거리 차로 현재 페이스를 낸다(D-14). 거리 차가 1m 미만이면 null. */
    private fun currentPace(): Pace? {
        if (window.size < 2) return null
        val (fromAt, fromMeters) = window.first()
        val (toAt, toMeters) = window.last()
        val meters = (toMeters - fromMeters).roundToLong()
        if (meters <= 0) return null
        return Pace.of(Duration.between(fromAt, toAt), Distance(meters))
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

    private companion object {
        val CURRENT_PACE_WINDOW: Duration = Duration.ofSeconds(30)
        val HEART_RATE_FRESHNESS: Duration = Duration.ofSeconds(30)
    }
}
