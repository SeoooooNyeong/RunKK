package com.a207.runkk.app.ui.record

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 문자열 리소스로 둔 날짜 서식(예: "M월 d일 (E)")으로 한국어 [DateTimeFormatter]를 만든다. */
@Composable
internal fun rememberDateFormatter(@StringRes pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    return remember(text) { DateTimeFormatter.ofPattern(text, Locale.KOREAN) }
}

/**
 * 거리별 최고 기록의 기준 거리 라벨(GET /records/growth bests의 키 = 서버 StandardDistance 이름): "KM5" → "5km",
 * "HALF" → "하프", "FULL" → "풀". 모르는 이름은 그대로 보인다.
 */
internal fun bestDistanceLabel(res: Resources, key: String): String = when (key) {
    "HALF" -> res.getString(R.string.record_distance_half)
    "FULL" -> res.getString(R.string.record_distance_full)
    else -> BEST_KM[key]?.let { RunkkFormat.targetLabel(Distance(it)) } ?: key
}

private val BEST_KM: Map<String, Long> = mapOf("KM1" to 1_000L, "KM3" to 3_000L, "KM5" to 5_000L, "KM10" to 10_000L)

/** 페이스를 분·초 기호로 적는다: 369초/km → 6'09". 없으면 -'--". */
internal fun paceMarks(pace: Pace?): String {
    val seconds = pace?.secondsPerKm ?: return NO_PACE
    return String.format(Locale.US, "%d'%02d\"", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** 가장 빠른 1km 구간 번호(1부터). 구간 페이스 최솟값의 위치이고 같으면 앞 구간, 구간이 없으면 null(D-280). */
internal fun fastestSplit(splits: List<Pace>): Int? =
    splits.withIndex().minByOrNull { it.value.secondsPerKm }?.let { it.index + 1 }

private const val NO_PACE = "-'--\""
private const val SECONDS_PER_MINUTE = 60
