package com.a207.runkk.app.domain.coach

import java.time.LocalDateTime

/** 이전 대화의 문답 한 건(S15P31A207-300). 본문은 ai 서버가 보관하고 api가 대화 주인을 확인한 뒤 내려준다. */
data class CoachMessage(val role: Role, val text: String, val at: LocalDateTime) {
    enum class Role { QUESTION, ANSWER }
}
