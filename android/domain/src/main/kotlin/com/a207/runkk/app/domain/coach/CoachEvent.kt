package com.a207.runkk.app.domain.coach

sealed interface CoachEvent {
    data class Started(val conversationId: String) : CoachEvent

    data class Delta(val text: String) : CoachEvent

    data object Done : CoachEvent

    data class Failed(val reason: CoachFailure) : CoachEvent
}
