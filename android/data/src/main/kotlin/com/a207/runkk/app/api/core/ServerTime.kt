package com.a207.runkk.app.api.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 서버 JSON 시각 형식(서버 시계는 Asia/Seoul의 LocalDateTime). 서버가 보낸 시각은 오프셋이 있으면 그대로, 없으면 Asia/Seoul로 읽고,
 * 앱이 보내는 시각은 Asia/Seoul의 ISO-8601 LocalDateTime(밀리초까지)으로 쓴다. 날짜는 ISO-8601(yyyy-MM-dd)이다.
 * api.*의 DTO 매퍼와 data.live의 실시간 메시지 변환이 함께 쓴다.
 */
internal object ServerTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun parse(text: String): Instant {
        val parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, OffsetDateTime::from, LocalDateTime::from)
        return when (parsed) {
            is OffsetDateTime -> parsed.toInstant()
            else -> (parsed as LocalDateTime).atZone(ZONE).toInstant()
        }
    }

    fun format(instant: Instant): String = LocalDateTime
        .ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZONE)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun parseDate(text: String): LocalDate = LocalDate.parse(text)

    fun formatDate(date: LocalDate): String = date.toString()
}
