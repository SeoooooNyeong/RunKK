package com.a207.runkk.app.data.device

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 안내를 소리·진동·화면으로 낸다(6.1.14 data.device, GuidanceOutput 구현). 소리 크기·출력 장치는 기기 설정을 따른다.
 * - hasVoice → TTS(한국어), hasVibration → 짧은 진동. 문구는 안내 키를 문자열 리소스로 바꾼 것이다(GuidanceTexts, M-3)
 * - 소리·진동이 모두 없는 안내(Guidance.screenOnly: 길게 누르기 안내, 저장 실패)는 화면 배너(토스트)로 보인다
 * 1초 시계·코루틴 어느 스레드에서 불려도 메인 스레드로 넘겨 차례대로 낸다.
 */
@Singleton
class AndroidGuidanceOutput
@Inject
constructor(@ApplicationContext private val context: Context) : GuidanceOutput {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val waiting = ArrayDeque<String>()

    override fun play(g: Guidance) {
        main.post { deliver(g) }
    }

    private fun deliver(g: Guidance) {
        val text = g.text(context.resources)
        if (g.hasVibration) vibrate()
        if (g.hasVoice) speak(text)
        if (!g.hasVoice && !g.hasVibration) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun speak(text: String) {
        val engine = tts ?: TextToSpeech(context, ::onTtsInit).also { tts = it }
        if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID) else waiting.addLast(text)
    }

    private fun onTtsInit(status: Int) {
        main.post {
            val engine = tts ?: return@post
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                engine.language = Locale.KOREAN
                while (waiting.isNotEmpty()) {
                    engine.speak(
                        waiting.removeFirst(),
                        TextToSpeech.QUEUE_ADD,
                        null,
                        UTTERANCE_ID
                    )
                }
            } else {
                waiting.clear()
            }
        }
    }

    private fun vibrate() {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val UTTERANCE_ID = "runkk-guidance"
        const val VIBRATION_MILLIS = 300L
    }
}
