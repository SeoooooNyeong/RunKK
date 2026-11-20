@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import android.Manifest
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * 기록런 탭(셀프런·셀럽런) 시작 때 위치 권한(S15P31A207-336): 권한이 없으면 대결을 만들기 전에 OS 권한 창을 먼저 띄우고, 답과
 * 상관없이 대결을 만들어 진행으로 간다. 대결을 만든 뒤에 물으면 답하는 동안 카운트다운이 흘러 측정 시작에 실패한다. 권한이 있으면 묻지
 * 않고 바로 간다. 권한 창은 가짜 결과 등록부로 대신한다.
 */
@RunWith(RobolectricTestRunner::class)
class RecordRunLocationPermissionTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()
    private val worldJob = Job()
    private lateinit var w: BattleWorld
    private val opened = mutableListOf<ViewModel>()
    private val navigated = mutableListOf<NavKey>()
    private val registry = FakePermissionRegistry()
    private val navigator = object : RunkkNavigator {
        override fun navigate(route: String) = Unit

        override fun navigate(key: NavKey) {
            navigated += key
        }

        override fun back() = Unit

        override fun replace(key: NavKey) = Unit

        override fun selectTab(route: String) = Unit

        override fun restart(route: String) = Unit
    }

    @Before
    fun setUp() {
        w = BattleWorld(CoroutineScope(StandardTestDispatcher(main.dispatcher.scheduler) + worldJob))
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(false)
    }

    @After
    fun tearDown() {
        opened.forEach { it.viewModelScope.cancel() }
        worldJob.cancel()
    }

    private fun <T : ViewModel> T.tracked(): T = also { opened += it }

    private fun show(content: @Composable () -> Unit) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalRunkkNavigator provides navigator,
                LocalActivityResultRegistryOwner provides owner
            ) {
                RunkkTheme(reduceMotion = true, content = content)
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        main.dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun showSelf() {
        val vm = SelfBattleViewModel(w.battles, w.coordinator).tracked()
        main.dispatcher.scheduler.runCurrent()
        vm.onSelect(vm.state.value.candidates.first { it.isSelectable })
        show { SelfBattleRoute(vm) }
        compose.onNodeWithText("기록 깨러 가기", substring = true).performClick()
        settle()
    }

    private fun showCeleb() {
        val vm = CelebBattleViewModel(w.battles, w.coordinator).tracked()
        main.dispatcher.scheduler.runCurrent()
        vm.onSelect(vm.state.value.candidates.first { it.isSelectable })
        show { CelebBattleRoute(vm) }
        compose.onNodeWithText(app.getString(R.string.battle_celeb_go)).performClick()
        settle()
    }

    @Test
    fun `셀프런은 위치 권한이 없으면 권한을 묻고 허용하면 대결 진행으로 간다`() {
        showSelf()
        assertEquals(1, registry.requests.size, "위치 권한 창을 띄운다")
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in registry.requests.single())
        assertEquals(emptyList(), navigated, "답하기 전에는 가지 않는다")
        assertTrue(w.battles.calls.none { it.startsWith("startSelf") }, "답하기 전에는 대결을 만들지 않는다: ${w.battles.calls}")

        registry.answer(granted = true)
        settle()
        assertTrue(w.battles.calls.any { it.startsWith("startSelf") }, "답한 뒤 대결을 만든다")
        assertIs<Routes.BattleRunning>(navigated.single())
        assertEquals(1, registry.requests.size, "다시 묻지 않는다")
    }

    @Test
    fun `셀럽런은 위치 권한이 없으면 권한을 묻고 거절해도 대결 진행으로 간다`() {
        showCeleb()
        assertEquals(1, registry.requests.size, "위치 권한 창을 띄운다")
        assertEquals(emptyList(), navigated)
        assertTrue(w.battles.calls.none { it.startsWith("startCeleb") }, "답하기 전에는 대결을 만들지 않는다: ${w.battles.calls}")

        registry.answer(granted = false)
        settle()
        assertIs<Routes.BattleRunning>(navigated.single())
        assertEquals(1, registry.requests.size, "거절해도 다시 묻지 않는다")
    }

    @Test
    fun `위치 권한이 있으면 묻지 않고 바로 대결 진행으로 간다`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        showSelf()
        assertEquals(emptyList(), registry.requests, "권한 창을 띄우지 않는다")
        assertIs<Routes.BattleRunning>(navigated.single())
    }
}

/** 권한 창 대신 요청을 적어 두고, 시험이 답을 돌려준다. */
private class FakePermissionRegistry : ActivityResultRegistry() {
    val requests = mutableListOf<List<String>>()
    private var lastCode = -1

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        lastCode = requestCode
        requests += (input as Array<*>).map { it.toString() }
    }

    fun answer(granted: Boolean) {
        val result = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to granted,
            Manifest.permission.ACCESS_COARSE_LOCATION to granted
        )
        dispatchResult(lastCode, result)
    }
}
