package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.model.Distance

/**
 * 내 러너 숫자 타일(피그마 제안 B안 62323:1642): 이번 주 달린 횟수·거리와 연속 러닝 일수(서버 DashboardView의 weekly 실제 값,
 * streakDays). 값마다 모르면 null이고 타일은 「-」를 보인다.
 */
data class RunnerStats(val weekRuns: Int?, val weekDistance: Distance?, val streakDays: Int?)
