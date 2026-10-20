package com.a207.runkk.watch.ui

/**
 * 워치 화면 상태. [WatchViewModel]이 만들고 [WatchHomeScreen]·[WatchRunDisplay]는 그리기만 한다.
 * running이 참이면 러닝 중 화면, 거짓이면 시작 화면(오늘 계획·시작 버튼, FR-042, N-21)이다.
 */
data class WatchUiState(
    val phoneConnected: Boolean = false,
    val running: Boolean = false,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Long = 0,
    val paceSecondsPerKm: Int? = null,
    /** 30초 넘게 새 값이 없거나 수집 금지면 null(빈칸, 6.1.15). */
    val heartRateBpm: Int? = null,
    val planDistanceMeters: Long? = null,
    val planPaceSecondsPerKm: Int? = null,
    /** 러닝 중 폰과 연결이 끊겼다(6.1.15 "연결 끊김 표시"). */
    val linkLost: Boolean = false,
    /** 시작 요청을 보내고 폰이 측정을 시작하기를 기다린다. */
    val startPending: Boolean = false,
    /** 폰이 수집을 허용했지만 워치 OS 심박 권한이 없다. */
    val heartRatePermissionNeeded: Boolean = false,
    /** 안내(폰의 notice 또는 "폰과 연결 필요", FR-183). */
    val notice: WatchNotice? = null
)

/** 워치에 잠깐 보이는 안내. 폰이 보낸 문구는 그대로, 워치가 정한 안내는 화면이 문자열 리소스로 바꾼다. */
sealed interface WatchNotice {
    data class FromPhone(val text: String) : WatchNotice

    /** FR-183 "연결되어 있지 않으면 거부하고 '폰과 연결 필요'를 보인다". */
    data object PhoneNotConnected : WatchNotice
}
