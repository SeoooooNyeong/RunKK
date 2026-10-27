package com.a207.runkk.watch.link

/**
 * 러닝 중 받은 마지막 근접 알림(FR-196, ProximityMessage가 켜짐일 때만). 화면(watch.ui)이 wear.contract 형식을 모르게 옮긴 값이다.
 * gapMeters는 절댓값이고 isAhead가 앞섬 여부, receivedAtMillis는 받은 시각(벽시계)이다.
 */
data class PhoneProximity(
    val gapMeters: Int,
    val isAhead: Boolean,
    val intervalMillis: Long,
    val receivedAtMillis: Long
)
