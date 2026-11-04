package com.a207.runkk.watch.ui

/**
 * 워치 시스템 위치(설정의 위치 켜기)가 켜져 있는지 답하는 포트(W52). 구현 [AndroidLocationSettingState].
 * 꺼져 있으면 앱 위치 권한이 있어도 Health Services가 운동 시작을 거부한다(폰 연결이 끊기면 워치 위치가 꺼지기도 한다).
 */
fun interface LocationSettingState {
    fun isEnabled(): Boolean
}
