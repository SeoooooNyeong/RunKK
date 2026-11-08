package com.a207.runkk.app.ui.record.runbody

import androidx.annotation.StringRes
import com.a207.runkk.app.ui.record.R

/** 요약 탭(G-14) 주간/월간 토글. 최근 10회 변화의 빈도 줄을 주간(회/주) 또는 월간(회/30일)으로 보인다(계약 v1.2 8.2). */
enum class RunBodyPeriod(@StringRes val labelRes: Int) {
    WEEK(R.string.record_runbody_period_week),
    MONTH(R.string.record_runbody_period_month)
}
