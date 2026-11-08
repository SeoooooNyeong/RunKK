package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome

/**
 * 이벤트 포트(이벤트 계약 2절, 5절). 구현 api.notification.EventRepositoryImpl.
 * 본 이벤트 ID는 기기 사본에 둔다(설정 사본 파일, 로그아웃 때 지운다).
 */
interface EventRepository {
    /** GET /events. 진행 중, 곧 열림, 지난 순이고 같은 상태 안은 sortOrder 순이다. */
    suspend fun events(): Outcome<List<Event>>

    /** GET /events/{slug}/me. */
    suspend fun me(slug: String): Outcome<EventEntry>

    /** 이벤트 그림(배너·썸네일) 바이트. [url]은 [Event]가 준 절대 주소다. */
    suspend fun image(url: String): Outcome<ByteArray>

    /** 상세 웹 페이지가 열려도 되는 origin(API 기준 주소의 scheme://host[:port]). 웹뷰 브리지는 이 origin에서만 동작한다. */
    fun webOrigin(): String

    /** 지금 쓰는 접근 토큰(웹뷰 브리지 getAccessToken). 로그아웃 상태면 null. */
    fun accessToken(): String?

    /** 이 기기에서 본 이벤트 ID. */
    suspend fun seenEventIds(): Set<Long>

    /** [ids]를 본 이벤트로 더한다. 지금 목록([current])에 없는 옛 ID는 버린다. */
    suspend fun markSeen(ids: Set<Long>, current: Set<Long>): Outcome<Unit>
}
