package com.a207.runkk.watch.ui

/** 워치 화면의 숫자 표기. 값이 없으면 [EMPTY](빈칸)로 둔다. */
internal object WatchFormat {
    const val EMPTY = "--"

    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
    private const val METERS_PER_10M = 10

    /** 근접 알림 세기 점 개수(와이어프레임 K5). */
    const val PROXIMITY_LEVELS = 7
    private const val NEAREST_INTERVAL_MILLIS = 300L
    private const val FARTHEST_INTERVAL_MILLIS = 2_000L
    private const val MILLIS_PER_TENTH = 100L

    /** 한 시간 전에는 "m:ss", 넘으면 "h:mm:ss". */
    fun elapsed(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val secs = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "$hours:${two(minutes)}:${two(secs)}"
        } else {
            "$minutes:${two(secs)}"
        }
    }

    /** km 소수 둘째 자리(내림). 4,329m → "4.32". */
    fun distanceKm(meters: Long): String {
        val tens = meters.coerceAtLeast(0) / METERS_PER_10M
        return "${tens / 100}.${two(tens % 100)}"
    }

    /** 분'초". 315 → 5'15". */
    fun pace(secondsPerKm: Int?): String {
        if (secondsPerKm == null || secondsPerKm <= 0) return EMPTY
        return "${secondsPerKm / SECONDS_PER_MINUTE}'${two((secondsPerKm % SECONDS_PER_MINUTE).toLong())}\""
    }

    fun heartRate(bpm: Int?): String = bpm?.toString() ?: EMPTY

    /** 근접 알림 간격을 초 소수 첫째 자리로(반올림). 800 → "0.8", 2000 → "2.0". */
    fun intervalSeconds(millis: Long): String {
        val tenths = (millis.coerceAtLeast(0) + MILLIS_PER_TENTH / 2) / MILLIS_PER_TENTH
        return "${tenths / 10}.${tenths % 10}"
    }

    /**
     * 근접 알림 세기 점 중 채울 개수(1~[PROXIMITY_LEVELS]). 간격이 짧을수록(가까울수록) 많다.
     * 간격 범위는 폰 규칙(ProximityAlertRule, FR-196)의 0.3초~2.0초다.
     */
    fun proximityLevel(intervalMillis: Long): Int {
        val clamped = intervalMillis.coerceIn(NEAREST_INTERVAL_MILLIS, FARTHEST_INTERVAL_MILLIS)
        val closeness =
            (FARTHEST_INTERVAL_MILLIS - clamped).toDouble() / (FARTHEST_INTERVAL_MILLIS - NEAREST_INTERVAL_MILLIS)
        return 1 + Math.round(closeness * (PROXIMITY_LEVELS - 1)).toInt()
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')
}
