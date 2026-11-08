package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * 한 번의 러닝 측정 상태를 보관한다(6.1.14, A-02). 시각은 모두 매개변수로 받는다.
 *
 * 상태 전이(상태도 2.4.7 v0.14): COUNTDOWN → start → RUNNING ⇄ (pause / resume) PAUSED, 측정 중 두 상태 → finish·end → FINISHED,
 * 자유런 RUNNING·PAUSED → discard(취소, 기록 없음) → FINISHED. hintFinish는 상태를 바꾸지 않는다(D-49).
 * 폰 버튼·워치 요청·알림 버튼의 정지·재개는 모두 pause·resume 하나로 모인다(D-256, D-260). 앱 화면 잠금은 없다(FR-182 삭제).
 */
class RunSession(val spec: RunStartSpec) {
    enum class State {
        COUNTDOWN,
        RUNNING,
        PAUSED,
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

    /** 열린 정지 구간의 시작 시각. 정지 중(PAUSED)일 때만 값이 있다. */
    private var pausedSince: Instant? = null

    private var distanceMeters = 0.0
    private val points = mutableListOf<RoutePoint>()
    private val unstable = mutableListOf<UnstableSegment>()
    private val heartRates = mutableListOf<HeartRateSample>()
    private val motions = mutableListOf<MotionSample>()

    /** 동행 워치가 잰 손목 케이던스. 기록을 확정할 때 폰 걸음 센서 케이던스보다 앞선다. */
    private val watchMotions = mutableListOf<MotionSample>()

    /** 포인트마다 (일시, 그때까지 누적 거리). 도달 시각 계산에 쓴다. */
    private val arrivals = mutableListOf<Pair<Instant, Double>>()

    /** 최근 30초 창의 (일시, 누적 거리). 현재 페이스 계산에 쓴다(D-14). */
    private val window = ArrayDeque<Pair<Instant, Double>>()
    private var lastNormal: FilteredPoint? = null
    private var lastSignal: SignalState? = null
    private var lastCurrentPace: Pace? = null

    /** 측정 중(정지 포함). 시작 전과 종료 뒤는 거짓이다. */
    val isActive: Boolean
        get() = state == State.RUNNING || isPaused

    /** 정지 중(PAUSED). */
    val isPaused: Boolean
        get() = state == State.PAUSED

    fun start(at: Instant) {
        check(state == State.COUNTDOWN) { "cannot start in state $state" }
        startedAt = at.atMillis()
        state = State.RUNNING
    }

    /**
     * 포인트를 반영한다. 정지 중이면 버린다(D-93 (1)). NORMAL이면 직전 정상 포인트와의 거리를 더하고, UNSTABLE이면 거리를 더하지 않고
     * 불안정 구간을 남긴다(Q-18). 재개 뒤 첫 포인트는 직전 정상 포인트가 없어 거리를 더하지 않는다.
     * 직전 포인트와의 시속이 25km/h를 넘으면(순간이동·차량 이동) 그 구간 거리도 더하지 않는다(FR-047 v0.10, [OverspeedRule]).
     * 경로에 남겼으면 참이다(RunTracker가 경로선에 같은 점을 더한다, D-261).
     */
    fun onLocation(p: FilteredPoint): Boolean {
        if (!isActive || isPaused) return false
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
        return true
    }

    /** 심박을 남긴다. 정지 중에도 남긴다(D-93 (1)). */
    fun onHeartRate(s: HeartRateSample) {
        if (!isActive) return
        heartRates += s
    }

    /**
     * 기기가 잰 속도·케이던스를 남긴다(S15P31A207-242). 정지 중에는 버린다(위치와 같다, D-93 (1)). 값이 없거나 음수·무한대면 버린다.
     * [isFromWatch]는 동행 워치의 손목 케이던스다.
     */
    fun onMotion(s: MotionSample, isFromWatch: Boolean = false) {
        if (!isActive || isPaused || !s.isUsable) return
        if (isFromWatch) watchMotions += s else motions += s
    }

    /**
     * 기록에 담을 움직임 표본(시간순). 워치 손목 케이던스가 하나라도 있으면 폰 걸음 센서 케이던스는 빼고(손목이 러닝 중 더 안정적이다,
     * 2026-11-08 결정) 폰 속도는 남긴다.
     */
    private fun recordedMotions(): List<MotionSample> {
        val phone = if (watchMotions.any { it.cadenceSpm != null }) {
            motions.map { it.copy(cadenceSpm = null) }.filter { it.isUsable }
        } else {
            motions
        }
        return (phone + watchMotions).sortedBy { it.at }
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
                heartRate = heartRates.lastOrNull(),
                isPaused = true,
                exactDistanceMeters = distanceMeters
            )
        }
        currentPace()?.let { lastCurrentPace = it }
        return RunProgress(
            elapsed = elapsed,
            distance = distance,
            currentPace = lastCurrentPace,
            averagePace = average,
            signal = lastSignal,
            heartRate = heartRates.lastOrNull()?.takeIf { Duration.between(it.at, now) <= HEART_RATE_FRESHNESS },
            exactDistanceMeters = distanceMeters
        )
    }

    /** 지금 정지 길이. 정지 중이 아니면 null. 정지 길이에는 상한이 없다(FR-195 삭제, D-257). */
    fun currentPause(now: Instant): Duration? = pausedSince?.let { Duration.between(it, maxOf(now, it)) }

    /** 정지 합(닫힌 구간 + 열린 구간). 운동 시간 계산에 쓴다. */
    internal fun pausedTotal(now: Instant): Duration {
        var sum = Duration.ZERO
        for (p in closedPauses) sum = sum.plus(Duration.between(p.from, p.to))
        return sum.plus(currentPause(now) ?: Duration.ZERO)
    }

    /**
     * 일시정지(FR-194): 자유런 RUNNING → PAUSED. 폰 버튼·워치 요청·알림 버튼이 모두 이 연산을 쓴다(D-256, D-260). [at]은 폰 처리 시각이다.
     * 그 밖의 모드·상태면 거짓이고 상태는 그대로다(D-93 (3), D-137).
     */
    fun pause(at: Instant): Boolean {
        if (!spec.mode.isPausable) return false
        if (state != State.RUNNING) return false
        pausedSince = at.atMillis()
        state = State.PAUSED
        return true
    }

    /** 재개(FR-194): PAUSED → RUNNING. 그 밖의 상태면 거짓이다. */
    fun resume(at: Instant): Boolean {
        if (state != State.PAUSED) return false
        closePause(at.atMillis())
        state = State.RUNNING
        return true
    }

    fun hintFinish(): Guidance = Guidance.screenOnly(Guidance.Kind.FINISH_HINT, GuidanceMessage.FINISH_HINT)

    /** 화면·워치의 종료(길게 누름). 정지 중에도 끝낼 수 있다(FR-036). */
    fun finish(now: Instant): FinishOutcome {
        check(isActive) { "cannot finish in state $state" }
        return complete(now)
    }

    /** 조정자의 종료(대결 종료 신호·도착·그만두기, D-36). */
    fun end(now: Instant): FinishOutcome {
        check(isActive) { "cannot end in state $state" }
        return complete(now)
    }

    /**
     * 기록을 남기지 않는 종료(자유런 취소, 셀프런·셀럽런 그만두기, D-266). 버릴 수 있는 카드 종류([isDiscardable])이고 측정 중일 때만
     * FINISHED로 바꾸고 참이다. 기록(CompletedRun)을 만들지 않는다. 정지 중에도 취소할 수 있고 열린 정지 구간은 버린다.
     */
    fun discard(): Boolean {
        if (!spec.cardKind.isDiscardable || !isActive) return false
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
        addPause(from, at)
        pausedSince = null
        lastResumedAt = at
        lastNormal = null
        window.clear()
        lastCurrentPace = null
    }

    /**
     * 종료: 열린 정지 구간은 기록 끝에 닫는다(D-93 (5)). 경과는 운동 시간이다.
     * 기록 끝은 시각 하나(밀리초)로 정하고 정지 구간 끝과 운동 시간을 모두 그 값으로 계산한다. 서버는 기록 끝을
     * 시작 + 운동 시간 + 정지 합으로 다시 만들어 정지 구간이 그 안에 있는지 보므로(F.2.2) 두 값이 1ms라도 갈리면 400이다(R12 D2).
     * 다른 스레드가 먼저 읽은 시각으로 정지·재개했으면 그 시각보다 앞으로 끝내지 않는다(from ≤ to).
     */
    private fun complete(now: Instant): FinishOutcome {
        val end = listOfNotNull(now.atMillis(), pausedSince, closedPauses.lastOrNull()?.to).max()
        pausedSince?.let { addPause(it, end) }
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
                elapsed = Duration.between(start, end).minus(pausedTotal(end)).coerceAtLeast(Duration.ZERO),
                distance = distance,
                route = points.toList(),
                unstable = unstable.toList(),
                heartRates = heartRates.toList(),
                pauses = closedPauses.toList(),
                startDevice = spec.startDevice,
                savedAt = now,
                motions = recordedMotions()
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

    /** 닫힌 정지 구간을 남긴다. 길이가 0이면(같은 밀리초 안의 정지·재개) 남기지 않는다: 서버는 from < to만 받는다. */
    private fun addPause(from: Instant, to: Instant) {
        if (to.isAfter(from)) closedPauses += PauseInterval(from, to)
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

    /** 세션이 남기는 시각은 밀리초까지만 둔다. 보관(epoch 밀리초)·전송(밀리초 세 자리)에서 잘려도 값이 바뀌지 않는다(R12 D2). */
    private fun Instant.atMillis(): Instant = truncatedTo(ChronoUnit.MILLIS)

    private companion object {
        val CURRENT_PACE_WINDOW: Duration = Duration.ofSeconds(30)
        val HEART_RATE_FRESHNESS: Duration = Duration.ofSeconds(30)
    }
}
