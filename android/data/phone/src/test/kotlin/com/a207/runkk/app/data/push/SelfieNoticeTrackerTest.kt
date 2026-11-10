package com.a207.runkk.app.data.push

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 셀카 결과 기기 알림(design-oo 05-30 [임시:D-성-120], 11절 4번): 얼굴 만들기 화면이 보이거나 앱이 앞에 있거나 알림 권한이 없으면 내지
 * 않고, 같은 작업은 한 번만 낸다.
 */
@RunWith(RobolectricTestRunner::class)
class SelfieNoticeTrackerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var isForeground = false
    private var canPost = true
    private val tracker = SelfieNoticeTracker(context, { isForeground }, { canPost })
    private val manager get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @BeforeTest
    fun setUp() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test
    fun `앱 밖이면 결과 알림을 한 건 내고 같은 작업을 다시 불러도 한 건이다`() {
        tracker.notify("job-1", succeeded = true)
        tracker.notify("job-1", succeeded = true)

        assertEquals(1, manager.allNotifications.size)
        assertEquals("새 얼굴이 준비됐어요", shadowOf(manager.allNotifications.single()).contentTitle)
    }

    @Test
    fun `실패도 알린다`() {
        tracker.notify("job-2", succeeded = false)

        assertEquals("얼굴을 만들지 못했어요", shadowOf(manager.allNotifications.single()).contentTitle)
    }

    @Test
    fun `얼굴 만들기 화면이 보이는 동안은 알리지 않는다`() {
        tracker.opened()
        assertTrue(tracker.isVisible)
        tracker.notify("job-1", succeeded = true)
        tracker.closed()
        assertFalse(tracker.isVisible)

        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun `앱이 앞에 있으면(화면이 바뀐다) 알리지 않는다`() {
        isForeground = true
        tracker.notify("job-1", succeeded = true)

        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun `알림 권한이 없으면 알리지 않는다`() {
        canPost = false
        tracker.notify("job-1", succeeded = true)

        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun `닫기를 연 것보다 많이 불러도 보이지 않음으로 둔다`() {
        tracker.closed()
        tracker.opened()

        assertTrue(tracker.isVisible)
    }
}
