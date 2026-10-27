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

/**
 * Vibrator로 [AlertVibrator]를 구현한다. 진동기가 없는 기기면 아무것도 하지 않는다.
 * 용도는 알람(USAGE_ALARM)이다: 화면이 꺼졌거나 러닝 중 포그라운드 서비스에서 울려도 시스템이 낮은 우선순위로 버리지 않게 한다.
 * API 33+는 VibrationAttributes, 그 아래는 AudioAttributes로 용도를 준다.
 */
class AndroidAlertVibrator @Inject constructor(@ApplicationContext context: Context) : AlertVibrator {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun vibrate(durationMillis: Long) {
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            @Suppress("DEPRECATION")
            target.vibrate(effect, attributes)
        }
    }
}
