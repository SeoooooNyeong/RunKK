package com.a207.runkk.app.ui.crew

sealed interface CrewRunDetailEvent {
    /** 글을 지웠다: 앞 화면으로. */
    data object Deleted : CrewRunDetailEvent
}
