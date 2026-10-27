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
 * ToneGenerator로 [AlertBeeper]를 구현한다. 미디어 음량(STREAM_MUSIC)을 따르고 블루투스 이어폰으로도 나간다.
 * 방해 금지 모드에서 들리지 않는 것은 받아들인다.
 * 재생이 시작되어 처음 울릴 때 장치를 열고, [release](재생 정지: 러닝 종료·근접 끔·만료)에서 놓는다.
 * 프로세스(워치 앱)가 끝나면 네이티브 자원도 함께 풀린다.
 * 열기에 실패하면 기록만 하고 그 재생 동안은 다시 열지 않으며(비프마다 실패 기록이 쌓이지 않게), 다음 재생 시작 때 다시 시도한다.
 */
@Singleton
class ToneGeneratorBeeper internal constructor(private val open: () -> Tone) : AlertBeeper {
    @Inject
    constructor() : this({ AndroidTone(ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)) })

    /** 열린 비프음 장치. 시험에서 ToneGenerator 대신 쓴다. */
    internal interface Tone {
        fun play(durationMillis: Int)

        fun release()
    }

    private val lock = Any()
    private var tone: Tone? = null
    private var hasFailedThisPlayback = false

    override fun beep(durationMillis: Int) {
        synchronized(lock) { toneLocked() }?.play(durationMillis)
    }

    override fun release() {
        val opened = synchronized(lock) {
            hasFailedThisPlayback = false
            tone.also { tone = null }
        }
        opened?.release()
    }

    private fun toneLocked(): Tone? {
        if (tone == null && !hasFailedThisPlayback) {
            tone = try {
                open()
            } catch (e: RuntimeException) {
                Timber.w(e, "비프음 장치를 열지 못했다. 다음 재생 시작 때 다시 시도한다")
                hasFailedThisPlayback = true
                null
            }
        }
        return tone
    }

    private class AndroidTone(private val generator: ToneGenerator) : Tone {
        override fun play(durationMillis: Int) {
            if (!generator.startTone(ToneGenerator.TONE_PROP_BEEP, durationMillis)) Timber.w("비프음을 내지 못했다")
        }

        override fun release() = generator.release()
    }

    private companion object {
        /** ToneGenerator 음량(0~100, 미디어 음량에 곱해진다). */
        const val VOLUME = 80
    }
}
