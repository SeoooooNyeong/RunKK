package com.a207.runkk.app.core.navigation

/** 화면 모듈끼리 의존하지 않고 한 화면에 다른 기능이 그림을 넣는 칸의 이름(D-255, D-298). */
enum class SlotKey {
    /** 기록 화면(ui.record)의 "대결" 탭. ui.battle이 대결 이력 탭을 넣는다(D-281). */
    RECORD_BATTLES,

    /** 홈 상단 알림함 단추 오른쪽 위 점. ui.notification이 안 본 진행 중 이벤트가 있을 때 라임 점을 넣는다(이벤트 계약 7절). */
    HOME_INBOX_DOT
}
