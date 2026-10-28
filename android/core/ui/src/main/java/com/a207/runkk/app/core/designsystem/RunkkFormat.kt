package com.a207.runkk.app.core.designsystem

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.util.Locale

/**
 * 수치 표시 규칙(core.designsystem → core.model: Distance·Pace 표시, 5.1.2). 화면마다 같은 모양으로 보이게 한 곳에 둔다.
 */
object RunkkFormat {
    /** 값이 없을 때. */
    const val EMPTY = "-"

    /** km 소수 둘째 자리: 5.02. */
    fun km(distance: Distance): String = String.format(Locale.US, "%.2f", distance.meters / METERS_PER_KM)

    /** km 소수 첫째 자리: 5.0(달력 칸). */
    fun kmShort(distance: Distance): String = String.format(Locale.US, "%.1f", distance.meters / METERS_PER_KM)

    /** 목표 거리 라벨: 5000m → "5km", 2500m → "2.5km". */
    fun targetLabel(distance: Distance): String {
        val km = distance.meters / METERS_PER_KM
        return if (km % 1.0 == 0.0) "${km.toLong()}km" else "${String.format(Locale.US, "%.1f", km)}km"
    }

    /** 페이스 m:ss(/km 단위 생략): 5:32. 없으면 "-:--". */
    fun pace(pace: Pace?): String {
        if (pace == null) return "-:--"
        val seconds = pace.secondsPerKm
        return String.format(Locale.US, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    /** 경과 시간 mm:ss 또는 h:mm:ss: 27:48, 1:02:03. */
    fun duration(duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** 보조 기술용 시간 읽기: "27분 48초", "1시간 2분 3초". */
    fun durationSpoken(res: Resources, duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return buildList {
            if (hours > 0) add(res.getString(R.string.core_spoken_hours, hours))
            if (minutes > 0 || hours > 0) add(res.getString(R.string.core_spoken_minutes, minutes))
            add(res.getString(R.string.core_spoken_seconds, seconds))
        }.joinToString(" ")
    }

    /** 보조 기술용 페이스 읽기: "1킬로미터당 5분 32초". */
    fun paceSpoken(res: Resources, pace: Pace?): String {
        if (pace == null) return res.getString(R.string.core_spoken_pace_none)
        val seconds = pace.secondsPerKm
        return res.getString(R.string.core_spoken_pace, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    /**
     * 보조 기술용 라벨 읽기: 영문 오버라인("LIVE BATTLE · 5km")과 거리 라벨("5km")을 한글("실시간 대결, 5킬로미터")로 바꾼다.
     * " · "로 나뉜 조각마다 바꾸고, 모르는 조각은 그대로 둔다.
     */
    fun spokenLabel(res: Resources, text: String): String = text.split(SEPARATOR).joinToString(", ") { raw ->
        val part = raw.trim()
        SPOKEN_WORDS[part]?.let(res::getString)
            ?: KM_LABEL.matchEntire(part)?.let { res.getString(R.string.core_spoken_km, it.groupValues[1]) }
            ?: part
    }

    /** [durationSpoken]을 화면에서 쓴다. */
    @Composable
    fun durationSpoken(duration: Duration): String = durationSpoken(LocalContext.current.resources, duration)

    /** [paceSpoken]을 화면에서 쓴다. */
    @Composable
    fun paceSpoken(pace: Pace?): String = paceSpoken(LocalContext.current.resources, pace)

    /** [spokenLabel]을 화면에서 쓴다. */
    @Composable
    fun spokenLabel(text: String): String = spokenLabel(LocalContext.current.resources, text)

    private const val SEPARATOR = " · "
    private val KM_LABEL = Regex("""(\d+(?:\.\d+)?)km""")
    private val SPOKEN_WORDS = mapOf(
        "LIVE BATTLE" to R.string.core_spoken_live_battle,
        "SELF BATTLE" to R.string.core_spoken_self_battle,
        "CELEB BATTLE" to R.string.core_spoken_celeb_battle,
        "BATTLE RUN" to R.string.core_spoken_battle_run,
        "MATCHED" to R.string.core_spoken_matched,
        "BATTLE RESULT" to R.string.core_spoken_battle_result,
        "RUNNING" to R.string.core_spoken_running,
        "PAUSED" to R.string.core_spoken_paused,
        "MY 러니멀" to R.string.core_spoken_my_runimal,
        "CREW RUN" to R.string.core_spoken_crew_run,
        "CREW RUN RESULT" to R.string.core_spoken_crew_run_result,
        "FREE RUN" to R.string.core_spoken_free_run,
        "FREE" to R.string.core_spoken_free,
        "KM" to R.string.core_spoken_kilometer,
        "AVG" to R.string.core_spoken_average
    )

    private const val METERS_PER_KM = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
}
