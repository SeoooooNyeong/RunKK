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
 * - 처음 값이거나 창 폭([TrackLayout.WINDOW_METERS])보다 크게 바뀌면 따라가지 않고 바로 옮긴다. 화면에서는 [TrackOtherMotion]이 그보다
 *   작은 큰 변화도 보이는 채로 옮기지 않고(사라졌다 나타남), 등장(출발 쪽 밖에서 들어오기·제자리 나타나기)도 정한다(4차).
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

    /** 받은 값이 있다(없으면 다음 값을 바로 보인다). */
    var hasValue = false
        private set

    /** [atSeconds]에 받은 거리 차 [meters]. */
    fun receive(meters: Double, atSeconds: Double) {
        if (!hasValue || abs(meters - shown) > TrackLayout.WINDOW_METERS) {
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
        val elapsed = (atSeconds - shownAt).coerceIn(0.0, MAX_FRAME_GAP)
        shown += (target(atSeconds) - shown) * (1 - exp(-elapsed / TIME_CONSTANT))
        shownAt = atSeconds
        return shown
    }

    /** 예측이 멈췄고 보이는 값이 거기 닿았다(더 그릴 것이 없다). */
    fun isSettled(atSeconds: Double): Boolean = !hasValue ||
        (
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
        hasValue = true
    }

    /** 받은 값을 잊는다. 다음 값은 따라가지 않고 바로 보인다(움직임 줄이기, 격차를 모르게 된 때). */
    fun reset() {
        hasValue = false
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
        const val MAX_FRAME_GAP = 0.034
    }
}
