package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes

/**
 * 크루 홈 탭 줄의 칸(와이어 E2): 레이스, 일정, 게시판, 채팅, 크루원. 공지, 투표, 응원은 게시판 칸 안의 칩이다(BoardFilter).
 * 채팅 칸은 탭 내용이 아니라 채팅방을 연다([tab]이 null).
 */
internal enum class CrewStripItem(@StringRes val labelRes: Int, val tab: CrewTab?) {
    RACE(R.string.crew_home_tab_race, CrewTab.RACE),
    SCHEDULE(R.string.crew_home_tab_schedule, CrewTab.SCHEDULE),
    BOARD(R.string.crew_home_tab_board, CrewTab.BOARD),
    CHAT(R.string.crew_home_chat, null),
    MEMBERS(R.string.crew_home_tab_members, CrewTab.MEMBERS);

    companion object {
        /** 지금 탭이 속한 칸. */
        fun of(tab: CrewTab): CrewStripItem = when (tab) {
            CrewTab.RACE -> RACE
            CrewTab.SCHEDULE -> SCHEDULE
            CrewTab.BOARD -> BOARD
            CrewTab.MEMBERS -> MEMBERS
        }
    }
}
