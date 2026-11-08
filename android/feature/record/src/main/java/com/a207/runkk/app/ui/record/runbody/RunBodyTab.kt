package com.a207.runkk.app.ui.record.runbody

import androidx.annotation.StringRes
import com.a207.runkk.app.ui.record.R

/** 런바디 화면 맨 위 탭(G-14 요약, G-13 러닝별, G-22 월간). */
enum class RunBodyTab(@StringRes val labelRes: Int) {
    SUMMARY(R.string.record_runbody_tab_summary),
    RUN(R.string.record_runbody_tab_run),
    MONTH(R.string.record_runbody_tab_month)
}
