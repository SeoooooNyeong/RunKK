package com.a207.runkk.app.ui.notification

import android.content.res.Resources
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 알림함 줄 오른쪽 상대 시간(F7): 1분 안 "방금", 1시간 안 "n분 전", 같은 날 "n시간 전", 전날 "어제", 일주일 안 "n일 전",
 * 그보다 오래되면 날짜. [now]가 없으면(미리보기) 날짜와 시각.
 */
internal fun inboxTime(res: Resources, receivedAt: Instant, now: Instant?, zone: ZoneId): String {
    if (now == null) {
        return DateTimeFormatter.ofPattern(res.getString(R.string.notification_time_pattern), Locale.KOREAN)
            .format(receivedAt.atZone(zone))
    }
    val elapsed = Duration.between(receivedAt, now).coerceAtLeast(Duration.ZERO)
    val days = ChronoUnit.DAYS.between(receivedAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    return when {
        elapsed.toMinutes() < 1 -> res.getString(R.string.notification_time_now)

        elapsed.toHours() < 1 -> res.getString(R.string.notification_time_minutes, elapsed.toMinutes().toInt())

        days == 0L -> res.getString(R.string.notification_time_hours, elapsed.toHours().toInt())

        days == 1L -> res.getString(R.string.notification_time_yesterday)

        days < WEEK_DAYS -> res.getString(R.string.notification_time_days, days.toInt())

        else -> DateTimeFormatter.ofPattern(res.getString(R.string.notification_date_pattern), Locale.KOREAN)
            .format(receivedAt.atZone(zone))
    }
}

private const val WEEK_DAYS = 7L
