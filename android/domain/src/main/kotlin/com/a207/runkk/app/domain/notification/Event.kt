package com.a207.runkk.app.domain.notification

import java.time.Instant

/**
 * 이벤트 한 건(GET /events, 이벤트 계약 2절). 목록은 앱 화면이 그리고 상세는 서버 웹 페이지(웹뷰)가 그린다.
 * 그림·상세 주소는 서버가 준 상대 경로를 API 기준 주소의 origin에 붙인 절대 주소이고, 상대 경로가 아니면 null이다(api.notification).
 * 상태는 서버가 지금 시각으로 센다.
 */
data class Event(
    val id: Long,
    val slug: String,
    val title: String,
    val subtitle: String?,
    val status: Status,
    val startsAt: Instant,
    val endsAt: Instant,
    val bannerImageUrl: String?,
    val thumbnailImageUrl: String?,
    val detailUrl: String?,
    val entryCount: Int,
    val maxEntryCount: Int,
    val sortOrder: Int
) {
    enum class Status { UPCOMING, ONGOING, ENDED }

    /** 응모가 한 번이라도 있는가(목록 상태 칩 "참여 중"). */
    val isEntered: Boolean get() = entryCount > 0
}

/** 진행 중인 이벤트 가운데 이 기기에서 아직 보지 않은 것이 있는가(알림함 항목·홈 알림함 점). */
fun List<Event>.hasUnseenOngoing(seenIds: Set<Long>): Boolean =
    any { it.status == Event.Status.ONGOING && it.id !in seenIds }
