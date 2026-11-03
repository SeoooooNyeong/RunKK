package com.a207.runkk.app.domain.coach

import java.time.LocalDateTime

data class CoachConversation(val conversationId: String, val firstQuestion: String, val lastAskedAt: LocalDateTime)
