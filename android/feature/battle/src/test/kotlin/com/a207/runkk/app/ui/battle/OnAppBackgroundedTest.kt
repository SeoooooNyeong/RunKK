package com.a207.runkk.app.ui.battle

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 매칭 화면의 앱 뒤로 감 감지(S15P31A207-278): Activity가 멈추면 부르고, 구성 변경으로 다시 만들어질 때는 부르지 않는다. */
@RunWith(RobolectricTestRunner::class)
class OnAppBackgroundedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var calls = 0

    @Test
    fun `S15P31A207-278 앱이 뒤로 가 Activity가 멈추면 부른다`() {
        compose.setContent { OnAppBackgrounded { calls++ } }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(1, calls)
    }

    @Test
    fun `S15P31A207-278 화면 회전 같은 구성 변경은 뒤로 감으로 보지 않는다`() {
        compose.setContent { OnAppBackgrounded { calls++ } }
        compose.activityRule.scenario.recreate()
        assertEquals(0, calls)
    }
}
