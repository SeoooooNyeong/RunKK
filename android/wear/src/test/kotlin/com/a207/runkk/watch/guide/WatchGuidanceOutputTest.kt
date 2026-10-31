package com.a207.runkk.watch.guide

import android.content.pm.PackageManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import com.a207.runkk.watch.testing.FakeAlertOutput
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.SchedulerMonotonicClock
import com.a207.runkk.watch.testing.T0
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** 3.2 WatchGuidanceOutput: 안내 하나를 음성·진동·화면 문구로 낸다(D-370, FR-253, FR-254, J-2). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchGuidanceOutputTest {
    private val context = RuntimeEnvironment.getApplication()

    private val split = Guidance(
        Guidance.Kind.SPLIT,
        GuidanceMessage.SPLIT,
        args = listOf("1"),
        hasVoice = true,
        hasVibration = true
    )

    private fun TestScope.output(hasSpeaker: Boolean = true): Pair<WatchGuidanceOutput, FakeAlertOutput> {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT, hasSpeaker)
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREAN)
        val alerts = FakeAlertOutput(SchedulerMonotonicClock(testScheduler))
        return WatchGuidanceOutput(context, alerts, SchedulerClock(testScheduler)) to alerts
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun lastTts(): ShadowTextToSpeech? = ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it) }

    @Test
    fun `음성 안내는 TTS가 준비되면 읽고 진동과 화면 문구를 함께 낸다`() = runTest {
        val (output, alerts) = output()

        output.play(split)
        idleMain()
        val tts = checkNotNull(lastTts()) { "TTS를 만들지 않았다" }
        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        idleMain()

        val text = context.getString(com.a207.runkk.app.api.core.R.string.guidance_split, "1")
        assertEquals(listOf(text), tts.spokenTextList)
        assertEquals(1, alerts.events.size)
        assertEquals(text, output.banner.value?.text)
        assertEquals(Instant.ofEpochMilli(T0), output.banner.value?.shownAt)
    }

    @Test
    fun `J-2 TTS 엔진이 없으면 음성 없이 진동과 화면 문구만 낸다`() = runTest {
        val (output, alerts) = output()

        output.play(split)
        idleMain()
        val tts = checkNotNull(lastTts())
        tts.onInitListener.onInit(TextToSpeech.ERROR)
        idleMain()
        output.play(split)
        idleMain()

        assertTrue(tts.spokenTextList.isEmpty())
        assertEquals(2, alerts.events.size)
        assertEquals(
            context.getString(com.a207.runkk.app.api.core.R.string.guidance_split, "1"),
            output.banner.value?.text
        )
    }

    @Test
    fun `스피커가 없고 블루투스 이어폰도 없으면 TTS를 열지 않는다`() = runTest {
        ShadowTextToSpeech.reset()
        val (output, alerts) = output(hasSpeaker = false)

        output.play(split)
        idleMain()

        assertNull(lastTts())
        assertEquals(1, alerts.events.size)
        assertEquals(
            context.getString(com.a207.runkk.app.api.core.R.string.guidance_split, "1"),
            output.banner.value?.text
        )
    }

    @Test
    fun `음성·진동이 꺼진 안내는 화면 문구만 낸다`() = runTest {
        ShadowTextToSpeech.reset()
        val (output, alerts) = output()

        output.play(Guidance.screenOnly(Guidance.Kind.PAUSE, GuidanceMessage.PAUSED))
        idleMain()

        assertNull(lastTts())
        assertTrue(alerts.events.isEmpty())
        assertEquals(context.getString(com.a207.runkk.app.api.core.R.string.guidance_paused), output.banner.value?.text)
    }

    @Test
    fun `화면 문구는 2초 뒤 만료된다`() {
        val banner = GuidanceBanner("1km 지점", Instant.ofEpochMilli(T0))

        assertFalse(banner.isExpired(Instant.ofEpochMilli(T0 + 1_999)))
        assertTrue(banner.isExpired(Instant.ofEpochMilli(T0 + 2_000)))
    }

    @Test
    fun `shutdown은 TTS를 닫는다`() = runTest {
        val (output, _) = output()
        output.play(split)
        idleMain()
        val tts = checkNotNull(lastTts())

        output.shutdown()
        idleMain()

        assertTrue(tts.isShutdown)
    }
}
