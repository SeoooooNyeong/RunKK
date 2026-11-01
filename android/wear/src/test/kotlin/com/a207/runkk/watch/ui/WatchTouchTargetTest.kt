package com.a207.runkk.watch.ui

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 글자 작은 알약 버튼도 누르는 영역은 48dp 이상이다(코드 리뷰 WR12-3). 보이는 모양만 40dp로 줄인다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchTouchTargetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `글자 12sp 알약 버튼의 누르는 영역은 48dp 이상이다`() {
        compose.setContent {
            WatchTheme { PillButton(text = "매칭 시작", onClick = {}, textSize = 12.sp) }
        }

        compose.onNodeWithText("매칭 시작").assertHeightIsAtLeast(48.dp)
    }
}
