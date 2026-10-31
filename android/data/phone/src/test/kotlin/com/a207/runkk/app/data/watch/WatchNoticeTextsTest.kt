package com.a207.runkk.app.data.watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.domain.run.WatchNotice
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 워치 안내 키(WatchNotice) → :data:phone 문자열 리소스(2차 리뷰 M-3). 안내 키 문구(data.guide)와 나뉜 뒤에도 빠짐이 없다. */
@RunWith(RobolectricTestRunner::class)
class WatchNoticeTextsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `모든 워치 안내 키에 문구가 있다`() {
        WatchNotice.entries.forEach { assertTrue(resources.getString(it.textRes()).isNotBlank(), it.name) }
    }
}
