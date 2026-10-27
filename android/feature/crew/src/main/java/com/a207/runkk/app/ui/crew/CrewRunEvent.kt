package com.a207.runkk.app.ui.crew

sealed interface CrewRunEvent {
    data class Finished(val postId: Long) : CrewRunEvent

    data object Left : CrewRunEvent
}
