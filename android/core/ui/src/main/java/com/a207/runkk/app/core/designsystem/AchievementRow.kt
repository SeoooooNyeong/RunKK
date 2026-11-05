package com.a207.runkk.app.core.designsystem

/** 성과 한 줄(「최장 거리 신기록」 / 「직전 4.80km」). [label]은 화면이 푸는 문구다(서버 문구는 UiText.Plain). */
data class AchievementRow(val icon: AchievementIcon, val label: UiText, val value: String)
