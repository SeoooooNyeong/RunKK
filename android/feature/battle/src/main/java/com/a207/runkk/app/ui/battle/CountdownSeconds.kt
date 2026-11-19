package com.a207.runkk.app.ui.battle

import java.time.Duration

/**
 * 출발 카운트다운에 보일 남은 초(S15P31A207-335 8회차). 남은 시간을 올려 세어 3초 카운트다운이 3·2·1로 보이고(내려 세면 화면이 붙는
 * 사이 남은 시간이 2.9초가 되어 첫 숫자가 2였다), 남은 시간이 정수 초를 지나는 순간에 다음 숫자로 바꾼다(처음 갱신부터 1초씩 세면
 * 숫자가 바뀌는 때가 최대 1초 어긋났다).
 */
internal object CountdownSeconds {
    private const val MILLIS_PER_SECOND = 1_000L

    /** [remaining]을 올린 초. 이미 지났으면 0이다. */
    fun left(remaining: Duration): Long {
        val millis = remaining.toMillis()
        return if (millis <= 0) 0 else (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    }

    /** 보이는 숫자([left])가 다음으로 바뀔 때까지(ms, 1~1000). 이미 지났으면 1초다. */
    fun untilNext(remaining: Duration): Long {
        val millis = remaining.toMillis()
        return if (millis <= 0) MILLIS_PER_SECOND else millis - (millis - 1) / MILLIS_PER_SECOND * MILLIS_PER_SECOND
    }
}
