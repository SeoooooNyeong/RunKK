package com.a207.runkk.app.ui.crew

sealed interface CrewEvent {
    /** 크루를 만들었다: 크루 탭을 새로 열어 내 크루 홈을 보인다. */
    data object Created : CrewEvent
}
