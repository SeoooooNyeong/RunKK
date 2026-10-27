package com.a207.runkk.app.ui.crew

sealed interface CrewHomeEvent {
    /** 크루를 나왔다: 크루 탭(찾기)으로. */
    data object Left : CrewHomeEvent
}
