package com.a207.runkk.app.ui.crew

/** 크루 검색 화면의 한 번 이벤트. */
sealed interface CrewSearchEvent {
    /** 가입됐다: 크루 탭(내 크루 홈)으로 간다. */
    data object Joined : CrewSearchEvent
}
