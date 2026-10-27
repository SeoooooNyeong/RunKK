package com.a207.runkk.app.ui.crew

sealed interface CrewRunWriteEvent {
    data object Posted : CrewRunWriteEvent
}
