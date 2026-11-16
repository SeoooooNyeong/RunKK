@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import android.Manifest
import android.app.Activity
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import androidx.test.espresso.Espresso
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.battle.FriendInvite
import kotlin.test.assertEquals
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
 * 대결 출발 전 절전 모드 안내([임시:FR-성-380], [임시:D-성-902]): 실제 Route와 ViewModel, 가짜 저장소로 매칭·초대·수락·기록런
 * 시작 요청보다 안내가 먼저인지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class BattleRunPowerSaveTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))
    private val worldJob = Job()
    private lateinit var w: BattleWorld
    private val opened = mutableListOf<ViewModel>()
    private var backs = 0
    private val navigator = object : RunkkNavigator {
        override fun navigate(route: String) = Unit

        override fun navigate(key: NavKey) = Unit

        override fun back() {
            backs++
        }

        override fun replace(key: NavKey) = Unit

        override fun selectTab(route: String) = Unit

        override fun restart(route: String) = Unit
    }
    private val sharer = object : InviteSharer {
        override suspend fun share(activity: Activity, invite: FriendInvite, inviterName: String?) =
            ShareResult.KAKAO_TALK
    }

    @Before
    fun setUp() {
        w = BattleWorld(CoroutineScope(StandardTestDispatcher(main.dispatcher.scheduler) + worldJob))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        opened.forEach { it.viewModelScope.cancel() }
        worldJob.cancel()
    }

    private fun <T : ViewModel> T.tracked(): T = also { opened += it }

    private fun matchViewModel() = MatchViewModel(
        w.battles,
        w.coordinator,
        w.channels,
        w.clock,
        consentOf(FakeConsentSettings(), w.accounts),
        w.accounts
    ).tracked()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalRunkkNavigator provides navigator) {
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

    private fun startAnyway() {
        compose.onNodeWithText("그냥 시작").performClick()
        settle()
    }

    private fun calls(prefix: String) = w.battles.calls.filter { it.startsWith(prefix) }

    @Test
    fun `TC-성-11070 랜덤 대결과 배치고사는 절전 모드면 매칭 요청 전에 묻고 그냥 시작하면 요청한다`() {
        power.setIsPowerSaveMode(true)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Quick(Distance(5_000)), sharer, vm) }
        assertEquals(emptyList(), calls("match:"), "안내 전에 매칭을 요청하지 않는다")
        assertEquals(0, w.battles.statusCalls)
        compose.onNode(isDialog()).assertExists()

        startAnyway()
        assertEquals(listOf("match:5000"), calls("match:"))
    }

    @Test
    fun `TC-성-11070 매칭 전 안내를 뒤로 가기로 닫으면 요청 없이 앞 화면으로 돌아간다`() {
        power.setIsPowerSaveMode(true)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Quick(Distance(5_000)), sharer, vm) }
        Espresso.pressBack()
        settle()
        assertEquals(1, backs)
        assertEquals(emptyList(), calls("match:"))
    }

    @Test
    fun `TC-성-11070 절전 모드가 꺼져 있으면 묻지 않고 바로 매칭을 요청한다`() {
        power.setIsPowerSaveMode(false)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Quick(Distance(5_000)), sharer, vm) }
        assertEquals(listOf("match:5000"), calls("match:"))
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `TC-성-11071 친구 대결 초대자는 초대를 만들기 전에 묻는다`() {
        power.setIsPowerSaveMode(true)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Invite(Distance(5_000)), sharer, vm) }
        assertEquals(emptyList(), calls("invite:"))
        compose.onNode(isDialog()).assertExists()

        startAnyway()
        assertEquals(listOf("invite:5000"), calls("invite:"))
    }

    @Test
    fun `TC-성-11072 친구 대결 수신자는 초대 내용을 먼저 보고 참가하기를 누르면 수락 전에 묻는다`() {
        power.setIsPowerSaveMode(true)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Join("tok"), sharer, vm) }
        assertEquals(listOf("inviteView:tok"), calls("inviteView:"), "초대 조회는 묻지 않고 한다")
        compose.onNode(isDialog()).assertDoesNotExist()

        compose.onNodeWithText(app.getString(R.string.battle_join_accept)).performClick()
        settle()
        compose.onNode(isDialog()).assertExists()
        assertEquals(emptyList(), calls("accept:"), "안내 전에 수락하지 않는다")

        startAnyway()
        assertEquals(listOf("accept:tok"), calls("accept:"))
    }

    @Test
    fun `TC-성-11072 수신자가 안내를 닫으면 수락하지 않고 참가 확인 화면에 남는다`() {
        power.setIsPowerSaveMode(true)
        val vm = matchViewModel()
        show { MatchRoute(MatchStart.Join("tok"), sharer, vm) }
        compose.onNodeWithText(app.getString(R.string.battle_join_accept)).performClick()
        settle()
        Espresso.pressBack()
        settle()
        assertEquals(0, backs)
        assertEquals(emptyList(), calls("accept:"))
        assertEquals(MatchPhase.CONFIRMING, vm.state.value.phase)
    }

    @Test
    fun `TC-성-11073 셀프런은 절전 모드면 시작 요청 전에 묻는다`() {
        power.setIsPowerSaveMode(true)
        val vm = SelfBattleViewModel(w.battles, w.coordinator).tracked()
        main.dispatcher.scheduler.runCurrent()
        vm.onSelect(vm.state.value.candidates.first { it.isSelectable })
        show { SelfBattleRoute(vm) }

        compose.onNodeWithText("기록 깨러 가기", substring = true).performClick()
        settle()
        compose.onNode(isDialog()).assertExists()
        assertEquals(emptyList(), calls("startSelf:"))

        startAnyway()
        assertEquals(1, calls("startSelf:").size)
    }

    @Test
    fun `TC-성-11073 셀럽런은 절전 모드면 시작 요청 전에 묻고 꺼져 있으면 바로 시작한다`() {
        power.setIsPowerSaveMode(true)
        val vm = CelebBattleViewModel(w.battles, w.coordinator).tracked()
        main.dispatcher.scheduler.runCurrent()
        vm.onSelect(vm.state.value.candidates.first { it.isSelectable })
        show { CelebBattleRoute(vm) }

        compose.onNodeWithText(app.getString(R.string.battle_celeb_go)).performClick()
        settle()
        compose.onNode(isDialog()).assertExists()
        assertEquals(emptyList(), calls("startCeleb:"))

        startAnyway()
        assertTrue(calls("startCeleb:").isNotEmpty())
    }
}
