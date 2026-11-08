package com.a207.runkk.app.ui.notification

import android.content.res.Resources
import com.a207.runkk.app.domain.notification.Event
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 카드 기간 줄: 진행 중은 "11월 16일까지", 곧 열림은 "11월 13일부터", 지난 이벤트는 "11월 16일 종료"(서울 날짜).
 */
internal fun eventPeriod(res: Resources, event: Event): String {
    val format = DateTimeFormatter.ofPattern(res.getString(R.string.event_date_pattern), Locale.KOREAN)
    return when (event.status) {
        Event.Status.ONGOING -> res.getString(
            R.string.event_period_until,
            format.format(event.endsAt.atZone(EVENT_ZONE))
        )

        Event.Status.UPCOMING -> res.getString(
            R.string.event_period_from,
            format.format(event.startsAt.atZone(EVENT_ZONE))
        )

        Event.Status.ENDED -> res.getString(R.string.event_period_ended, format.format(event.endsAt.atZone(EVENT_ZONE)))
    }
}

/**
 * D-day까지 남은 날 수: 진행 중은 끝나는 날까지, 곧 열림은 시작하는 날까지(서울 날짜). 지난 이벤트와 기준 날짜가 없으면 null.
 * 0이면 그날이다(D-DAY).
 */
internal fun eventDaysLeft(event: Event, today: LocalDate?): Long? {
    if (today == null) return null
    val target = when (event.status) {
        Event.Status.ONGOING -> event.endsAt
        Event.Status.UPCOMING -> event.startsAt
        Event.Status.ENDED -> return null
    }
    return ChronoUnit.DAYS.between(today, target.atZone(EVENT_ZONE).toLocalDate()).coerceAtLeast(0)
}

/** "D-8", 그날이면 "D-DAY". */
internal fun eventDdayLabel(res: Resources, daysLeft: Long): String = if (daysLeft ==
    0L
) {
    res.getString(R.string.event_dday_today)
} else {
    res.getString(R.string.event_dday, daysLeft.toInt())
}
