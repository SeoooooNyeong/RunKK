package com.a207.runkk.watch.ui

/**
 * 워치 화면 상태. [WatchViewModel]이 만들고 [WatchHomeScreen]·[WatchRunDisplay]는 그리기만 한다.
 * isRunning이 참이면 러닝 중 화면(정지 중이면 일시정지 화면, 근접 알림 중이면 대결 화면), 거짓이면 시작 화면(오늘 계획·시작 버튼,
 * FR-042, N-21)이고 폰과 연결되어 있지 않으면 미연결 화면이다(와이어프레임 K1~K5).
 */
data class WatchUiState(
    val isPhoneConnected: Boolean = false,
    val isRunning: Boolean = false,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Long = 0,
    val paceSecondsPerKm: Int? = null,
    /** 30초 넘게 새 값이 없거나 수집 금지면 null(빈칸, 6.1.15). */
    val heartRateBpm: Int? = null,
    val planDistanceMeters: Long? = null,
    val planPaceSecondsPerKm: Int? = null,
    /** 러닝 중 폰과 연결이 끊겼다(6.1.15 "연결 끊김 표시"). */
    val isLinkLost: Boolean = false,
    /** 시작 요청을 보내고 폰이 측정을 시작하기를 기다린다. */
    val isStartPending: Boolean = false,
    /** 폰이 수집을 허용했지만 워치 OS 심박 권한이 없다. */
    val isHeartRatePermissionNeeded: Boolean = false,
    /** 안내(폰의 notice 또는 "폰과 연결 필요", FR-183). */
    val notice: WatchNotice? = null,
    /** 심박 수집이 처음 켜졌고 알림 권한이 없어 한 번 물어야 한다(API 33+). 창을 띄운 뒤 화면이 알린다. */
    val isNotificationPermissionNeeded: Boolean = false,
    /** 폰이 측정을 정지했다(FR-194). 지표는 폰이 정지 시작 때 값으로 고정해 보낸다. */
    val isPaused: Boolean = false,
    /** 자유런 측정 중이라 워치에서 일시정지·재개를 요청할 수 있다(대결·크루런·옛 폰 앱은 거짓, F.4.2). */
    val isPausable: Boolean = false,
    /** 대결 중 근접 알림이 켜져 있다(FR-196). 이때 러닝 화면 대신 근접 알림 화면(와이어프레임 K5)을 보인다. */
    val proximity: WatchProximity? = null
) {
    /** 러닝 화면에 둘 일시정지 조작(6.1.15 WatchRunDisplay, TC-6569). */
    val pauseControl: PauseControl
        get() = when {
            !isPausable -> PauseControl.NONE
            isPaused -> PauseControl.RESUME
            else -> PauseControl.PAUSE
        }
}
