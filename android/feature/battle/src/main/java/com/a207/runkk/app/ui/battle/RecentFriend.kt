package com.a207.runkk.app.ui.battle

import java.time.LocalDate

/** 최근 함께 달린 친구 한 명(친구 대결 이력, D-492). [lastRunOn]은 마지막으로 함께 달린 날이다. */
data class RecentFriend(val memberId: Long, val name: String, val lastRunOn: LocalDate)
