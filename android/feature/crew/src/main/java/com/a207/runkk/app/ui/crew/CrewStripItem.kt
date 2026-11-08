package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes

/** 크루 홈 탭 줄의 칸(피그마 F-05): 레이스, 크루런, 채팅. 채팅 칸은 탭 내용이 아니라 채팅방을 연다([tab]이 null). */
internal enum class CrewStripItem(@StringRes val labelRes: Int, val tab: CrewTab?) {
    RACE(R.string.crew_home_tab_race, CrewTab.RACE),
    SCHEDULE(R.string.crew_home_tab_crew_run, CrewTab.SCHEDULE),
    CHAT(R.string.crew_home_chat, null);

    companion object {
        /** 지금 탭이 속한 칸. */
        fun of(tab: CrewTab): CrewStripItem = when (tab) {
            CrewTab.RACE -> RACE
            CrewTab.SCHEDULE -> SCHEDULE
        }
    }
}
