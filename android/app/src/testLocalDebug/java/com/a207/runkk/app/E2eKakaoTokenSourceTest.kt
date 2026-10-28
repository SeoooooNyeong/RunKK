package com.a207.runkk.app

import android.app.Application
import com.a207.runkk.app.core.model.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** E2E 시험용 카카오 창구: 토큰 값이 없으면 꺼지고(Kakao SDK 그대로), 있으면 앱 키 없이 그 토큰을 돌려준다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class E2eKakaoTokenSourceTest {
    @Test
    fun `토큰 값이 비면 대체 창구를 넣지 않는다`() {
        assertEquals(emptySet(), e2eKakaoTokenSources(""))
        assertEquals(emptySet(), e2eKakaoTokenSources("   "))
    }

    @Test
    fun `토큰 값이 있으면 앱 키 없이 그 토큰을 돌려준다`() = runTest {
        val source = e2eKakaoTokenSources(" e2e-1001 ").single()

        assertFalse(source.isNativeAppKeyRequired)
        assertEquals(
            Outcome.Success("e2e-1001"),
            source.accessToken(RuntimeEnvironment.getApplication(), nativeAppKey = "")
        )
    }
}
