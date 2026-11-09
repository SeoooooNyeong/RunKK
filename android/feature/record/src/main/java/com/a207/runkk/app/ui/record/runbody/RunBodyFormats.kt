package com.a207.runkk.app.ui.record.runbody

import androidx.annotation.StringRes
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.ui.record.R
import java.time.Duration
import java.util.Locale

/**
 * 페이스를 피그마처럼 분·초 기호(프라임 ′, 겹프라임 ″)로 적는다: 348초/km → 5′48″. 없으면 -′--″. 보조 기술에는
 * [com.a207.runkk.app.core.designsystem.RunkkFormat.paceSpoken]("5분 48초")을 쓴다.
 */
internal fun runBodyPace(pace: Pace?): String {
    val seconds = pace?.secondsPerKm ?: return NO_PACE
    return String.format(Locale.US, "%d′%02d″", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** 짧은 기록 시간 m:ss(개인 최고 1km 4:52, 2km 10:05). */
internal fun runBodyShortTime(duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
}

/** km 소수 첫째 자리(5.2). 구간 길이처럼 작은 값은 소수 둘째 자리까지 버리지 않는다(0.25). */
internal fun runBodyKm(km: Double): String {
    if (km >= 1.0) return String.format(Locale.US, "%.1f", km)
    return String.format(Locale.US, "%.2f", km).trimEnd('0').trimEnd('.')
}

/** 심박 변화(bpm). 음수는 피그마처럼 빼기 기호(−)로 적는다. */
internal fun runBodySigned(value: Int): String = if (value < 0) "−${-value}" else "$value"

/** 부하 비율 소수 둘째 자리(1.14). */
internal fun runBodyRatio(ratio: Double): String = String.format(Locale.US, "%.2f", ratio)

@StringRes
internal fun RunBodyAxis.labelRes(): Int = when (this) {
    RunBodyAxis.INTENSITY -> R.string.record_runbody_axis_intensity
    RunBodyAxis.ENDURANCE -> R.string.record_runbody_axis_endurance
    RunBodyAxis.STABILITY -> R.string.record_runbody_axis_stability
    RunBodyAxis.FORM -> R.string.record_runbody_axis_form
    RunBodyAxis.FITNESS -> R.string.record_runbody_axis_fitness
}

@StringRes
internal fun RunBodyResult.Coaching.titleRes(): Int = when (this) {
    RunBodyResult.Coaching.WARMUP_MISSING -> R.string.record_runbody_coaching_warmup
    RunBodyResult.Coaching.POSITIVE_SPLIT -> R.string.record_runbody_coaching_positive_split
    RunBodyResult.Coaching.OVERSTRIDE -> R.string.record_runbody_coaching_overstride
    RunBodyResult.Coaching.RECOVERY_SLOWER -> R.string.record_runbody_coaching_recovery
    RunBodyResult.Coaching.ACWR_HIGH -> R.string.record_runbody_coaching_acwr
}

@StringRes
internal fun RunBodyMonth.Load.Level.labelRes(): Int = when (this) {
    RunBodyMonth.Load.Level.LOW -> R.string.record_runbody_load_low
    RunBodyMonth.Load.Level.NORMAL -> R.string.record_runbody_load_normal
    RunBodyMonth.Load.Level.HIGH -> R.string.record_runbody_load_high
}

/** 러닝 시작 시각(0~23시)의 때 이름: 새벽 0~4, 아침 5~10, 낮 11~16, 저녁 17~20, 밤 21~23. */
@StringRes
internal fun timeOfDayRes(hour: Int): Int = when (hour) {
    in 0..DAWN_END -> R.string.record_runbody_dawn
    in 0..MORNING_END -> R.string.record_runbody_morning
    in 0..DAYTIME_END -> R.string.record_runbody_daytime
    in 0..EVENING_END -> R.string.record_runbody_evening
    else -> R.string.record_runbody_night
}

private const val NO_PACE = "-′--″"
private const val SECONDS_PER_MINUTE = 60
private const val DAWN_END = 4
private const val MORNING_END = 10
private const val DAYTIME_END = 16
private const val EVENING_END = 20
