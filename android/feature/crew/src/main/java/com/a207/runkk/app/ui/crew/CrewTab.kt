package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes

/**
 * 크루 홈 탭(피그마 F-05·F-11): 레이스, 크루런(크루런 일정만). 탭 줄의 채팅 칸은 탭 내용이 아니라 채팅방을 연다(CrewStripItem).
 * 크루 정보와 크루원은 머리글 ⋯ 메뉴로 여는 쪽(CrewHomePage)이다.
 */
enum class CrewTab(@StringRes val labelRes: Int) {
    RACE(R.string.crew_home_tab_race),
    SCHEDULE(R.string.crew_home_tab_crew_run)
}
