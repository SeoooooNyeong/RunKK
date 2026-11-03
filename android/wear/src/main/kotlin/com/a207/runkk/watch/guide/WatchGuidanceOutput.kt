package com.a207.runkk.watch.guide

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.a207.runkk.app.data.guide.GuidanceTexts
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.watch.alert.AlertVibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * 안내 하나를 음성·진동·화면 문구로 낸다(3.2, D-370, FR-253, FR-254). 워치 단독 측정의 GuidanceOutput 구현이다.
 *
 * - 음성: hasVoice인 안내를 TextToSpeech로 읽는다. 용도는 길 안내 음성(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)이라 블루투스 이어폰이
 *   연결되어 있으면 시스템이 그쪽으로 보낸다. 스피커(FEATURE_AUDIO_OUTPUT)도 블루투스 출력도 없거나, TTS 엔진이 없거나 한국어를
 *   못 읽으면 음성을 내지 않는다(J-2). 음성·진동을 낼지는 안내를 만든 domain 규칙이 서버 설정(음성안내여부·진동안내여부)으로 이미
 *   정했다(Guidance.hasVoice·hasVibration).
 * - 진동: hasVibration이면 짧게.
 * - 화면: 모든 안내를 [banner]에 문구로 2초 올린다. 문구는 폰과 같은 GuidanceTexts(data.guide, D-387)다.
 * 1초 시계·코루틴 어느 스레드에서 불려도 메인 스레드로 넘겨 차례대로 낸다.
 */
@Singleton
class WatchGuidanceOutput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vibrator: AlertVibrator,
    private val clock: Clock
) : GuidanceOutput {
    private val main = Handler(Looper.getMainLooper())

    /** 아래 TTS 상태는 메인 스레드에서만 읽고 쓴다. */
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    /** TTS 엔진을 열지 못했다(엔진 없음·한국어 없음). 이 프로세스에서는 다시 열지 않는다. */
    private var isTtsUnavailable = false
    private val waiting = ArrayDeque<String>()

    private val _banner = MutableStateFlow<GuidanceBanner?>(null)

    /** 측정 화면 위에 띄울 안내 문구. 2초 뒤 null로 돌아간다. */
    val banner: StateFlow<GuidanceBanner?> = _banner.asStateFlow()

    private val clearBanner = Runnable { _banner.value = null }

    override fun play(g: Guidance) {
        main.post { deliver(g) }
    }

    /** TTS를 닫는다. 다음 음성 안내가 오면 다시 연다. */
    fun shutdown() {
        main.post {
            tts?.shutdown()
            tts = null
            isTtsReady = false
            waiting.clear()
        }
    }

    private fun deliver(g: Guidance) {
        val text = GuidanceTexts.text(g, context.resources)
        if (g.hasVibration) vibrate()
        if (g.hasVoice) speak(text)
        showBanner(text)
    }

    private fun showBanner(text: String) {
        main.removeCallbacks(clearBanner)
        _banner.value = GuidanceBanner(text, clock.instant())
        main.postDelayed(clearBanner, GuidanceBanner.SHOWN_FOR.toMillis())
    }

    private fun vibrate() {
        try {
            vibrator.vibrate(VIBRATION_MILLIS)
        } catch (e: RuntimeException) {
            Timber.w(e, "안내 진동을 내지 못했다")
        }
    }

    private fun speak(text: String) {
        if (isTtsUnavailable || !hasAudioOutput()) return
        val engine = tts ?: openEngine()
        if (isTtsReady) {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        } else {
            // 엔진이 늦게 열리면 지난 안내를 몰아 읽지 않게 가장 최근 것 몇 개만 남긴다.
            waiting.addLast(text)
            while (waiting.size > MAX_WAITING) waiting.removeFirst()
        }
    }

    /** 엔진을 연다. 초기화 결과는 그 엔진에 묶는다(닫고 다시 연 뒤 옛 엔진의 늦은 결과가 새 엔진에 쓰이지 않게). */
    private fun openEngine(): TextToSpeech {
        var created: TextToSpeech? = null
        return TextToSpeech(context) { status -> main.post { onTtsInit(created, status) } }
            .also {
                created = it
                tts = it
            }
    }

    /** 메인 스레드에서 부른다. 이미 닫았거나 다른 엔진으로 바뀌었으면 무시한다. */
    private fun onTtsInit(initialized: TextToSpeech?, status: Int) {
        val engine = tts?.takeIf { it === initialized } ?: return
        isTtsReady = status == TextToSpeech.SUCCESS && prepare(engine)
        if (isTtsReady) {
            while (waiting.isNotEmpty()) engine.speak(waiting.removeFirst(), TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        } else {
            Timber.i("TTS를 쓸 수 없어 진동·화면 문구로만 안내한다")
            isTtsUnavailable = true
            waiting.clear()
            engine.shutdown()
            tts = null
        }
    }

    /** 한국어와 길 안내 음성 용도를 정한다. 한국어를 읽을 수 없으면 거짓. */
    private fun prepare(engine: TextToSpeech): Boolean {
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val language = engine.setLanguage(Locale.KOREAN)
        return language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** 워치 스피커나 블루투스 오디오 출력이 있는가(FR-253 "스피커가 없고 이어폰도 없으면 음성을 내지 않는다"). */
    private fun hasAudioOutput(): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) return true
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in BLUETOOTH_OUTPUTS }
    }

    private companion object {
        const val UTTERANCE_ID = "runkk-watch-guidance"
        const val VIBRATION_MILLIS = 300L
        const val MAX_WAITING = 2

        /** 블루투스 출력 종류. BLE 헤드셋 상수는 API 31부터지만 값만 비교하므로 API 30 기기에서는 그 종류가 나오지 않을 뿐이다. */
        @SuppressLint("InlinedApi")
        val BLUETOOTH_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET
        )
    }
}
