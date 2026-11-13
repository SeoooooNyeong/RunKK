package com.a207.runkk.app.core.avatar

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.test.Test
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 아바타 뷰는 터치를 받지 않는다. 칸 위의 Compose 클릭(홈 아바타 → 내 러너, [임시:FR-성-17])이 Rive 뷰에 먹히지 않게 하는 실기기 결함
 * (2026-11-11 3차 확인, 홈 아바타를 눌러도 내 러너로 가지 않음)의 회귀 시험이다.
 */
@RunWith(RobolectricTestRunner::class)
class RiveAvatarViewTouchTest {
    @Test
    fun `아바타 뷰는 누르기를 소비하지 않는다`() {
        val context = RuntimeEnvironment.getApplication()
        val view = RiveAvatarView(context)
        // Rive 플레이어(RiveAnimationView)처럼 누르기를 소비하는 자식
        val player = View(context).apply { isClickable = true }
        view.addView(player, ViewGroup.LayoutParams(100, 100))
        view.layout(0, 0, 100, 100)
        player.layout(0, 0, 100, 100)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        assertFalse(view.dispatchTouchEvent(down))
        down.recycle()
    }
}
