package com.a207.runkk.app.ui.crew

/** 크루 가입 확인 화면이 다른 화면으로 가는 때([임시:D-성-244]). */
sealed interface CrewInviteJoinEvent {
    /** 가입했거나 이미 구성원이다: 그 크루 홈(Routes.CrewHome)으로. */
    data class OpenCrewHome(val crewId: Long) : CrewInviteJoinEvent

    /** 만료 안내의 「크루 찾기」: 크루 검색(Routes.CREW_SEARCH)으로. */
    data object OpenSearch : CrewInviteJoinEvent
}
