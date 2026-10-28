package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes

/**
 * 크루 홈 탭(와이어 E2): 레이스, 일정(크루런, 대면 모임), 게시판(공지, 투표, 응원, 게시글, 앨범 칩), 크루원.
 * 탭 줄의 채팅 칸은 탭 내용이 아니라 채팅방을 연다(CrewStripItem).
 */
enum class CrewTab(@StringRes val labelRes: Int) {
    RACE(R.string.crew_home_tab_race),
    SCHEDULE(R.string.crew_home_tab_schedule),
    BOARD(R.string.crew_home_tab_board),
    MEMBERS(R.string.crew_home_tab_members)
}
