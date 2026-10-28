package com.a207.runkk.watch.ui

/**
 * 대결 근접 알림 화면에 보일 값(와이어프레임 K5, FR-196). gapMeters는 절댓값, isAhead가 앞섬, intervalMillis는 폰이 정한 알림 간격이다.
 * 근접 알림이 켜진 동안(마지막 메시지 뒤 3초 안)에만 [WatchUiState.proximity]에 들어 있다.
 */
data class WatchProximity(val gapMeters: Int, val isAhead: Boolean, val intervalMillis: Long)
