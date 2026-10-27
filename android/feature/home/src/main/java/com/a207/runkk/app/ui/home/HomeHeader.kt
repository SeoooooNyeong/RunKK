package com.a207.runkk.app.ui.home

/** 홈 상단(하이파이 HiHome): 닉네임, 티어·레벨, 리워드 잔액. 못 읽은 값은 null이고 화면은 그 자리를 비워 둔다. */
data class HomeHeader(
    val nickname: String? = null,
    val tier: String? = null,
    val level: Int? = null,
    val balance: Int? = null
)
