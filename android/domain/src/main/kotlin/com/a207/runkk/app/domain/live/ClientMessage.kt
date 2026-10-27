package com.a207.runkk.app.domain.live

/** 앱 → 서버 실시간 메시지(F.3.2). 하위 타입은 ProgressReport(JSON type "Progress")와 ChatPresence 둘이다(D-103). */
sealed interface ClientMessage

/**
 * 채팅방 입장·퇴장 신호(JSON type "ChatPresence", FR-209, FR-218). [isPresent]의 JSON 키는 present다(D-111, D-97).
 * 위치 항목이 없다(NFR-021).
 */
data class ChatPresence(val crewId: Long, val isPresent: Boolean) : ClientMessage
