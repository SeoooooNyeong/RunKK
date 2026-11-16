package com.a207.runkk.app.ui.home

import android.Manifest
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.run.TodayPlanCache
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** 자유런 출발 전 절전 모드 안내([임시:FR-성-380], [임시:D-성-902]): 홈의 실제 Route와 ViewModel로 이동 전에 묻는지 본다. */
@RunWith(RobolectricTestRunner::class)
class HomeRunPowerSaveTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))
    private val settings = FakeSettingsRepository().apply { isLocationConsented = true }
    private val account = FakeAccountRepository()
    private val routes = mutableListOf<String>()
    private val navigator = object : RunkkNavigator {
        override fun navigate(route: String) {
            routes += route
        }

        override fun navigate(key: NavKey) = Unit

        override fun back() = Unit

        override fun replace(key: NavKey) = Unit

        override fun selectTab(route: String) = Unit

        override fun restart(route: String) = Unit
    }
    private val viewModel = HomeViewModel(
        FakeRecordRepository(),
        account,
        LocationConsentUseCase(account, settings),
        TodayPlanCache(),
        FakePendingRunRepository(),
        Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    )

    @Before
    fun grantLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun showHome() = compose.setContent {
        CompositionLocalProvider(LocalRunkkNavigator provides navigator) {
            RunkkTheme(reduceMotion = true) { HomeRoute(viewModel) }
        }
    }

    private fun tapFreeRun() {
        compose.runOnIdle { viewModel.onFreeRunTapped() }
        compose.waitForIdle()
    }

    @Test
    fun `TC-성-11069 절전 모드면 자유런 이동 전에 안내하고 그냥 시작을 눌러야 러닝 화면으로 간다`() {
        power.setIsPowerSaveMode(true)
        showHome()
        tapFreeRun()
        assertEquals(emptyList(), routes, "안내 전에 러닝 화면으로 가지 않는다")
        compose.onNode(isDialog()).assertExists()

        compose.onNodeWithText("그냥 시작").performClick()
        assertEquals(listOf(Routes.RUN), routes)

        tapFreeRun()
        assertEquals(listOf(Routes.RUN, Routes.RUN), routes, "같은 홈 화면에서는 다시 묻지 않는다")
    }

    @Test
    fun `TC-성-11069 절전 모드가 꺼져 있으면 묻지 않고 러닝 화면으로 간다`() {
        power.setIsPowerSaveMode(false)
        showHome()
        tapFreeRun()
        assertEquals(listOf(Routes.RUN), routes)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `TC-성-11067 홈의 옛 배터리 최적화 안내 문자열을 지웠다`() {
        val fields = R.string::class.java.fields
        assertTrue(fields.none { it.name.startsWith("home_run_battery_") })
        val strings = fields.map { app.getString(it.getInt(null)) }
        assertTrue(strings.none { "제한 없음" in it || "배터리 최적화" in it })
    }
}
