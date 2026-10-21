package com.a207.runkk.app.core.designsystem

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

    /** 목표 거리 라벨: 5000m → "5K", 2500m → "2.5K". */
    fun targetLabel(distance: Distance): String {
        val km = distance.meters / METERS_PER_KM
        return if (km % 1.0 == 0.0) "${km.toLong()}K" else "${String.format(Locale.US, "%.1f", km)}K"
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
    fun durationSpoken(duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return buildList {
            if (hours > 0) add("${hours}시간")
            if (minutes > 0 || hours > 0) add("${minutes}분")
            add("${seconds}초")
        }.joinToString(" ")
    }

    /** 보조 기술용 페이스 읽기: "1킬로미터당 5분 32초". */
    fun paceSpoken(pace: Pace?): String {
        if (pace == null) return "페이스 없음"
        val seconds = pace.secondsPerKm
        return "1킬로미터당 ${seconds / SECONDS_PER_MINUTE}분 ${seconds % SECONDS_PER_MINUTE}초"
    }

    /**
     * 보조 기술용 라벨 읽기: 영문 오버라인("LIVE BATTLE · 5K")과 거리 라벨("5K")을 한글("실시간 대결, 5킬로미터")로 바꾼다.
     * " · "로 나뉜 조각마다 바꾸고, 모르는 조각은 그대로 둔다.
     */
    fun spokenLabel(text: String): String = text.split(SEPARATOR).joinToString(", ") { raw ->
        val part = raw.trim()
        SPOKEN_WORDS[part] ?: KM_LABEL.matchEntire(part)?.let { "${it.groupValues[1]}킬로미터" } ?: part
    }

    private const val SEPARATOR = " · "
    private val KM_LABEL = Regex("""(\d+(?:\.\d+)?)K""")
    private val SPOKEN_WORDS = mapOf(
        "LIVE BATTLE" to "실시간 대결",
        "SELF BATTLE" to "나와의 싸움",
        "CELEB BATTLE" to "유명인 대결",
        "BATTLE RUN" to "배틀런",
        "MATCHED" to "매칭 성사",
        "BATTLE RESULT" to "대결 결과",
        "RUNNING" to "측정 중",
        "MY RUNIMAL" to "내 Runimal",
        "CREW RUN" to "크루런",
        "CREW RUN RESULT" to "크루런 결과",
        "FREE RUN" to "자유런",
        "FREE" to "목표 없음",
        "KM" to "킬로미터",
        "AVG" to "평균"
    )

    private const val METERS_PER_KM = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
}
