package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes

/** 이벤트 목록 탭(피그마 이벤트 모아보기): 진행 중(진행 중과 곧 열림), 지난 이벤트. */
enum class EventTab(@StringRes val labelRes: Int, @StringRes val headerRes: Int, @StringRes val emptyRes: Int) {
    ONGOING(R.string.event_tab_ongoing, R.string.event_list_header_ongoing, R.string.event_list_empty_ongoing),
    PAST(R.string.event_tab_past, R.string.event_list_header_past, R.string.event_list_empty_past)
}
