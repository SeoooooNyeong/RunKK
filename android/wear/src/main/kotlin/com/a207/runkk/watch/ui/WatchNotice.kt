package com.a207.runkk.watch.ui

/** 워치에 잠깐 보이는 안내. 폰이 보낸 문구는 그대로, 워치가 정한 안내는 화면이 문자열 리소스로 바꾼다. */
sealed interface WatchNotice {
    data class FromPhone(val text: String) : WatchNotice

    /** FR-183 "연결되어 있지 않으면 거부하고 '폰과 연결 필요'를 보인다". */
    data object PhoneNotConnected : WatchNotice

    /** FR-181 종료 버튼을 짧게 눌렀다. 실수로 끝나지 않게 길게 눌러야 한다. */
    data object HoldToEnd : WatchNotice
}
