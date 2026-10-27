package com.a207.runkk.watch.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** SystemClock.elapsedRealtime으로 [MonotonicClock]을 구현한다(잠든 시간 포함, 시각 변경에 흔들리지 않음). */
class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
