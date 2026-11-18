package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.exp

/**
 * 트랙 상대 위치 보간(S15P31A207-335). 거리 차(상대 − 나, m)는 약 1초마다 정수로 온다. 받은 대로 옮기면 1 m(약 12dp) 계단이 되므로
 * 매 프레임 보이는 값을 따로 계산한다. 시각은 초 단위이고 프레임 시계(withFrameNanos)와 같은 것을 쓴다.
 *
 * - 예측: 최근 [VELOCITY_WINDOW]초 안 받은 값으로 상대 속도를 재고 지수 평균한다(|v| ≤ [MAX_SPEED] m/s). 마지막 기준점에서 그 속도로
 *   [MAX_EXTRAPOLATION]초까지만 이어 가고, 예측값은 마지막 받은 값에서 [MAX_DEVIATION] m 안에 둔다. 그 뒤에도 새 값이 없으면(수신 끊김)
 *   마지막 받은 값으로 돌아가 멈춘다.
 * - 기준점 보정: 새 값이 예측과 1 m 안이면 차이의 일부만 고친다(정수로 잘린 값 때문에 앞뒤로 흔들리지 않게). 더 크면 받은 값으로 옮긴다.
 * - 보이는 값: 예측값을 시간 상수 [TIME_CONSTANT]초로 따라간다. 기준점이 바뀌어도 보이는 값은 튀지 않는다.
 * - 처음 값이거나 창 폭([TrackLayout.WINDOW_METERS])보다 크게 바뀌면 따라가지 않고 바로 옮긴다.
 * - 등장([enter], 2차): 처음 값을 트랙 밖 출발 쪽 자리에서 시작하게 둔다. [startEntry] 전(몸 준비 전)에는 그 자리에 머물고, 시작하면
 *   [ENTRY_SECONDS] 동안 감속하며 예측값으로 들어온다. 그 뒤는 위와 같다.
 * - 프레임 루프가 쉬었다가(그릴 것이 없어 멈춘 동안) 다시 그려도 쉰 시간만큼 한 번에 따라가지 않는다(한 프레임은 [MAX_FRAME_GAP]초, 60 fps 두 프레임까지로 본다).
 */
internal class GapMotion {
    private val samples = ArrayDeque<Pair<Double, Double>>()
    private var anchor = 0.0
    private var anchorAt = 0.0
    private var last = 0.0
    private var velocity = 0.0
    private var shown = 0.0
    private var shownAt = 0.0
    private var entryFrom = 0.0
    private var entryStartAt = Double.NaN

    /** 트랙 밖에서 들어오는 중이다(시작 전 기다림 포함). */
    private var isEntering = false

    /** 받은 값이 있다(없으면 다음 값을 바로 보인다). */
    var hasValue = false
        private set

    /** [atSeconds]에 받은 거리 차 [meters]. */
    fun receive(meters: Double, atSeconds: Double) {
        // 들어오는 동안 보이는 값은 트랙 밖이라 받은 값과 견주지 않는다.
        if (!hasValue || abs(meters - if (isEntering) last else shown) > TrackLayout.WINDOW_METERS) {
            snap(meters, atSeconds)
            return
        }
        // 멈춰 있던(프레임 루프가 쉰) 뒤라면 쉰 시간을 따라가기 시간으로 치지 않는다. 다음 프레임은 이 시각부터 잰다.
        if (isSettled(atSeconds)) shownAt = atSeconds
        val predicted = predicted(atSeconds)
        samples.addLast(atSeconds to meters)
        while (samples.size > 1 && atSeconds - samples.first().first > VELOCITY_WINDOW) samples.removeFirst()
        val (oldestAt, oldest) = samples.first()
        val measured = if (atSeconds > oldestAt) (meters - oldest) / (atSeconds - oldestAt) else velocity
        // 첫 측정(값 두 개)은 그대로 쓴다. 0에서 반씩 다가가면 출발 직후 몇 초 동안 예측이 늦어 값이 올 때마다 고친다.
        val gain = if (samples.size == 2) 1.0 else VELOCITY_GAIN
        velocity = (velocity + gain * (measured - velocity)).coerceIn(-MAX_SPEED, MAX_SPEED)
        val error = meters - predicted
        anchor = if (abs(error) <= SMALL_ERROR) predicted + error * ANCHOR_GAIN else meters
        anchorAt = atSeconds
        last = meters
    }

    /** [atSeconds] 프레임에 보일 거리 차. */
    fun frame(atSeconds: Double): Double {
        if (!hasValue) return 0.0
        if (isEntering) return enteringFrame(atSeconds)
        val elapsed = (atSeconds - shownAt).coerceIn(0.0, MAX_FRAME_GAP)
        shown += (target(atSeconds) - shown) * (1 - exp(-elapsed / TIME_CONSTANT))
        shownAt = atSeconds
        return shown
    }

    private fun enteringFrame(atSeconds: Double): Double {
        shownAt = atSeconds
        if (entryStartAt.isNaN()) return shown
        val progress = ((atSeconds - entryStartAt) / ENTRY_SECONDS).coerceIn(0.0, 1.0)
        // 감속(ease-out quad): 빠르게 들어와 제자리에서 멈춘다. 끝에서 예측값과 같아져 이어서 따라간다.
        val eased = 1 - (1 - progress) * (1 - progress)
        shown = entryFrom + (target(atSeconds) - entryFrom) * eased
        if (progress >= 1.0) isEntering = false
        return shown
    }

    /** 예측이 멈췄고 보이는 값이 거기 닿았다(더 그릴 것이 없다). */
    fun isSettled(atSeconds: Double): Boolean = !hasValue ||
        (isEntering && entryStartAt.isNaN()) ||
        (
            !isEntering &&
                (isStale(atSeconds) || (velocity == 0.0 && anchor == last)) &&
                abs(target(atSeconds) - shown) < SETTLED_METERS
            )

    /** 따라가지 않고 [meters]로 바로 옮긴다(첫 값, 큰 변화, 움직임 줄이기). */
    fun snap(meters: Double, atSeconds: Double) {
        samples.clear()
        samples.addLast(atSeconds to meters)
        anchor = meters
        anchorAt = atSeconds
        last = meters
        velocity = 0.0
        shown = meters
        shownAt = atSeconds
        isEntering = false
        hasValue = true
    }

    /**
     * 처음 값 [meters]를 받되 보이는 값은 [fromMeters](트랙 밖 출발 쪽)에서 시작한다. [startEntry]까지 그 자리에 머문다(몸이 준비되기
     * 전에는 들어오지 않는다).
     */
    fun enter(meters: Double, fromMeters: Double, atSeconds: Double) {
        snap(meters, atSeconds)
        shown = fromMeters
        entryFrom = fromMeters
        entryStartAt = Double.NaN
        isEntering = true
    }

    /** 기다리던 등장을 [atSeconds]부터 시작한다. 등장 중이 아니거나 이미 시작했으면 아무것도 하지 않는다. */
    fun startEntry(atSeconds: Double) {
        if (isEntering && entryStartAt.isNaN()) {
            entryStartAt = atSeconds
            shownAt = atSeconds
        }
    }

    /** 받은 값을 잊는다. 다음 값은 따라가지 않고 바로 보인다(움직임 줄이기, 격차를 모르게 된 때). */
    fun reset() {
        hasValue = false
        isEntering = false
        samples.clear()
    }

    private fun predicted(atSeconds: Double): Double =
        anchor + velocity * (atSeconds - anchorAt).coerceIn(0.0, MAX_EXTRAPOLATION)

    private fun target(atSeconds: Double): Double = if (isStale(atSeconds)) {
        last
    } else {
        predicted(atSeconds).coerceIn(last - MAX_DEVIATION, last + MAX_DEVIATION)
    }

    private fun isStale(atSeconds: Double) = atSeconds - anchorAt > MAX_EXTRAPOLATION

    private companion object {
        const val TIME_CONSTANT = 0.25
        const val MAX_SPEED = 4.0
        const val MAX_EXTRAPOLATION = 1.5
        const val MAX_DEVIATION = 3.0
        const val VELOCITY_WINDOW = 2.0
        const val VELOCITY_GAIN = 0.5
        const val SMALL_ERROR = 1.0
        const val ANCHOR_GAIN = 0.3
        const val SETTLED_METERS = 0.001
        const val ENTRY_SECONDS = 0.9
        const val MAX_FRAME_GAP = 0.034
    }
}
