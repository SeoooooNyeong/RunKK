package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes

/**
 * 크루 홈 탭(피그마 F-05·F-09·F-11): 레이스, 크루런(크루런 일정만), 채팅(ui.chat ChatTabContent, D-653). 크루 정보와
 * 크루원은 머리글 ⋯ 메뉴로 여는 쪽(CrewHomePage)이다. 경로 Routes.CrewHome.tab은 이 이름이다.
 */
enum class CrewTab(@StringRes val labelRes: Int) {
    RACE(R.string.crew_home_tab_race),
    SCHEDULE(R.string.crew_home_tab_crew_run),
    CHAT(R.string.crew_home_chat);

    companion object {
        /** 경로의 탭 이름 → 탭. 없거나 모르면 [fallback]. */
        fun of(name: String?, fallback: CrewTab = RACE): CrewTab = entries.firstOrNull { it.name == name } ?: fallback
    }
}
