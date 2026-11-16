@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * 크루런 준비 완료 전 절전 모드 안내([임시:FR-성-380], [임시:D-성-902]): 실제 Route와 ViewModel, 가짜 저장소로 준비 요청보다 안내가
 * 먼저인지, 준비 해제는 묻지 않는지, 이미 준비한 대기실에 다시 들어오면 준비를 그대로 둔 채 확인하는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class CrewRunPowerSaveTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))
    private val worldJob = Job()
    private lateinit var w: CrewWorld
    private lateinit var vm: CrewRunViewModel

    @Before
    fun setUp() {
        w = CrewWorld(CoroutineScope(StandardTestDispatcher(main.dispatcher.scheduler) + worldJob))
        vm = CrewRunViewModel(w.crews, w.coordinator, w.clock, ServerClock(w.clock))
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        worldJob.cancel()
    }

    private fun showLobby() {
        compose.setContent { RunkkTheme(reduceMotion = true) { CrewRunRoute(Routes.CrewRunLobby(31), vm) } }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        main.dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
    }

    /** 참가자 줄의 「준비 완료」 표시가 아니라 누를 수 있는 준비 완료 단추를 누른다. */
    private fun tapReady() {
        compose.onNode(hasText(app.getString(R.string.crew_run_ready)) and hasClickAction()).performClick()
        settle()
    }

    private fun readyCalls() = w.crews.calls.filter { it.startsWith("ready:") }

    @Test
    fun `TC-성-11074 절전 모드면 준비 완료 요청 전에 묻고 그냥 시작하면 준비한다`() {
        power.setIsPowerSaveMode(true)
        showLobby()
        compose.onNode(isDialog()).assertDoesNotExist()

        tapReady()
        compose.onNode(isDialog()).assertExists()
        assertEquals(emptyList(), readyCalls(), "안내 전에 준비하지 않는다")

        compose.onNodeWithText("그냥 시작").performClick()
        settle()
        assertEquals(listOf("ready:31"), readyCalls())
        assertTrue(vm.state.value.isReady)
    }

    @Test
    fun `TC-성-11074 준비를 풀 때는 묻지 않는다`() {
        power.setIsPowerSaveMode(false)
        showLobby()
        tapReady()
        assertTrue(vm.state.value.isReady)

        power.setIsPowerSaveMode(true)
        tapReady()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("ready:31", "ready:31"), readyCalls())
        assertFalse(vm.state.value.isReady)
    }

    @Test
    fun `TC-성-11075 이미 준비한 대기실에 다시 들어오면 준비를 그대로 둔 채 한 번 확인한다`() {
        power.setIsPowerSaveMode(true)
        w.crews.lobbyState = lobby.copy(
            participants = lobby.participants.map { if (it.isMe) it.copy(isReady = true) else it }
        )
        showLobby()
        assertTrue(vm.state.value.isReady)
        compose.onNode(isDialog()).assertExists()

        compose.onNodeWithText("그냥 시작").performClick()
        settle()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(emptyList(), readyCalls(), "준비 상태를 뒤집지 않는다")
        assertTrue(vm.state.value.isReady)
    }
}
