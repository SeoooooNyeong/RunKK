package com.a207.runkk.watch.alert

/** 진동 포트. 구현 [AndroidAlertVibrator]는 Vibrator를 감싼다. 기다리지 않고 바로 돌아온다. */
interface AlertVibrator {
    fun vibrate(durationMillis: Long)
}
