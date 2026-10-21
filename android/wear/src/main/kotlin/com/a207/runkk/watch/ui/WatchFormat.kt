package com.a207.runkk.watch.ui

/** 워치 화면의 숫자 표기. 값이 없으면 [EMPTY](빈칸)로 둔다. */
internal object WatchFormat {
    const val EMPTY = "--"

    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
    private const val METERS_PER_10M = 10

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

    private fun two(value: Long): String = value.toString().padStart(2, '0')
}
