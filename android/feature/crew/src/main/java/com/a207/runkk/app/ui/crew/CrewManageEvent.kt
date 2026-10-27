package com.a207.runkk.app.ui.crew

sealed interface CrewManageEvent {
    /** 크루를 지웠다: 크루 탭으로. */
    data object Deleted : CrewManageEvent
}
