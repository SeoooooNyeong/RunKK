package com.a207.runkk.watch.ui

import android.animation.ValueAnimator
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * WR16-1: 움직임 줄이기 판단은 화면이 떠 있는 동안 바뀐 절전 모드와 애니메이션 배율을 따라간다(측정 화면은 러닝 내내 떠 있다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchMotionReducedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val power = shadowOf(context.getSystemService(PowerManager::class.java))
    private var reduced: Boolean? = null

    private fun show() {
        compose.setContent { reduced = rememberMotionReduced() }
        compose.waitForIdle()
    }

    /**
     * 개발자 옵션·접근성 애니메이터 배율(숨은 API)을 바꾸고, 시스템처럼 등록된 배율 변경 리스너(API 33)에 알린다. Robolectric 그림자는
     * 배율만 바꾸고 리스너에 알리지 않아 등록 목록을 직접 부른다.
     */
    private fun setDurationScale(scale: Float) {
        ReflectionHelpers.callStaticMethod<Unit>(
            ValueAnimator::class.java,
            "setDurationScale",
            ClassParameter.from(Float::class.javaPrimitiveType, scale)
        )
        val listeners: List<WeakReference<ValueAnimator.DurationScaleChangeListener>> =
            ReflectionHelpers.getStaticField(ValueAnimator::class.java, "sDurationScaleChangeListeners")
        listeners.toList().mapNotNull { it.get() }.forEach { it.onChanged(scale) }
    }

    @After
    fun restore() {
        power.setIsPowerSaveMode(false)
        setDurationScale(1f)
    }

    @Test
    fun `WR16-1 화면이 떠 있는 동안 절전 모드를 켜고 끄면 따라간다`() {
        show()
        assertEquals(false, reduced)

        power.setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(true, reduced)

        power.setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(false, reduced)
    }

    @Test
    fun `WR16-1 화면이 떠 있는 동안 애니메이션 배율을 0으로 바꾸면 따라간다`() {
        show()
        assertEquals(false, reduced)

        setDurationScale(0f)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()

        assertEquals(0f, ValueAnimator.getDurationScale())
        assertEquals(true, reduced)
    }
}
