package com.a207.runkk.app.ui.notification

/** 이벤트 웹 페이지가 RunkkApp.open(target)으로 여는 앱 화면(이벤트 계약 5절). 모르는 값은 무시한다. */
enum class EventPageTarget(val value: String) {
    /** 셀럽런 목록(기록런 탭 셀럽런). */
    CELEBRITY("celebrity"),

    /** 자유런 시작 준비 화면. */
    RUN("run"),

    /** 이벤트 목록. */
    EVENTS("events");

    companion object {
        fun of(value: String?): EventPageTarget? = entries.firstOrNull { it.value == value }
    }
}
