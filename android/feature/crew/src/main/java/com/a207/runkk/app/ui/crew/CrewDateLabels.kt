package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "10.28"(와이어의 짧은 날짜). 틀은 문자열 리소스(crew_date_short_pattern)다. */
internal fun shortDateLabel(resources: Resources, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    at.atZone(
        zone
    ).format(DateTimeFormatter.ofPattern(resources.getString(R.string.crew_date_short_pattern), Locale.KOREAN))

/** 화면에서 쓰는 [shortDateLabel]. */
@Composable
internal fun shortDateLabel(at: Instant): String = shortDateLabel(LocalContext.current.resources, at)

/** "10.21 (수) 19:30"(와이어 I4 일정 일시). */
internal fun scheduleLabel(resources: Resources, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    at.atZone(
        zone
    ).format(DateTimeFormatter.ofPattern(resources.getString(R.string.crew_schedule_pattern), Locale.KOREAN))

/** 화면에서 쓰는 [scheduleLabel]. */
@Composable
internal fun scheduleLabel(at: Instant): String = scheduleLabel(LocalContext.current.resources, at)
