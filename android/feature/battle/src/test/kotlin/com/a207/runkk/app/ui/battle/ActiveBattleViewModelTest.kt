@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 앱을 다시 켰을 때 서버에 남은 대결(배틀 QA 7번): 그만두기로 끝내 새 대결 잠금을 푼다. */
class ActiveBattleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val active = ActiveBattle(9, BattleHistory.Type.RANDOM, "IN_PROGRESS", Distance(1_000), "단단한치타", null)

    private fun ViewModelCloser.vm(w: BattleWorld) = track(ActiveBattleViewModel(w.battles, w.coordinator, w.screens))

    @Test
    fun `이 프로세스가 맡지 않은 진행 중 대결이 있으면 안내하고 그만두면 포기를 보낸 뒤 닫는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.activeResult = Outcome.Success(active)
        val vm = vms.vm(w)
        runCurrent()
        assertEquals(active, vm.state.value.battle)
        vm.onForfeit()
        runCurrent()
        assertEquals(listOf("active", "forfeit:9"), w.battles.calls)
        assertNull(vm.state.value.battle)
    }

    @Test
    fun `AN10-2 셀프런·셀럽런이 남았으면 취소로 끝내고 그 밖·종류 모름은 그만두기다(D-338)`() = main.runVmTest { vms ->
        val expected =
            mapOf(
                BattleHistory.Type.SELF to "cancel:9",
                BattleHistory.Type.CELEBRITY to "cancel:9",
                BattleHistory.Type.RANDOM to "forfeit:9",
                BattleHistory.Type.GHOST to "forfeit:9",
                BattleHistory.Type.FRIEND to "forfeit:9",
                null to "forfeit:9"
            )
        expected.forEach { (type, call) ->
            val w = world()
            w.battles.activeResult = Outcome.Success(active.copy(type = type))
            val vm = vms.vm(w)
            runCurrent()
            vm.onForfeit()
            runCurrent()
            assertEquals(listOf("active", call), w.battles.calls, "종류 $type")
            assertNull(vm.state.value.battle)
        }
    }

    @Test
    fun `지금 달리고 있는 대결이면 안내하지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.coordinator.onMatched(matchFound(battleId = 9))
        runCurrent()
        w.battles.activeResult = Outcome.Success(active)
        val vm = vms.vm(w)
        runCurrent()
        assertNull(vm.state.value.battle)
    }

    @Test
    fun `진행 중인 대결이 없거나 읽지 못하면 안내하지 않는다`() = main.runVmTest { vms ->
        val w = world()
        val none = vms.vm(w)
        w.battles.activeResult = Outcome.Failure(AppError.Network)
        val failed = vms.vm(w)
        runCurrent()
        assertNull(none.state.value.battle)
        assertNull(failed.state.value.battle)
    }

    @Test
    fun `그만두기가 실패하면 안내를 남겨 다시 하거나 닫을 수 있다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.activeResult = Outcome.Success(active)
        w.battles.forfeitResult = Outcome.Failure(AppError.Network)
        val vm = vms.vm(w)
        runCurrent()
        vm.onForfeit()
        runCurrent()
        assertEquals(active, vm.state.value.battle)
        assertEquals(BattleErrorText.of(AppError.Network), vm.state.value.error)
        vm.onDismiss()
        assertNull(vm.state.value.battle)
        assertNull(vm.state.value.error)
    }

    private val running = Routes.BattleRunning(9, "바람처럼", 5_000, OpponentKind.RANDOM.name)
    private val home = Routes.Key(Routes.HOME)

    /** 이 프로세스가 대결 9를 열고 진행 화면 키를 적어 둔 상태(최근 앱에서 밀어 닫아 Activity만 사라진 뒤). */
    private fun TestScope.openRound(w: BattleWorld) {
        w.coordinator.onMatched(matchFound(battleId = 9))
        runCurrent()
        w.screens.remember(running)
        w.battles.activeResult = Outcome.Success(active)
    }

    private fun TestScope.opened(vm: ActiveBattleViewModel): List<Routes.BattleRunning> {
        val opened = mutableListOf<Routes.BattleRunning>()
        backgroundScope.launch { vm.returns.collect { opened += it } }
        runCurrent()
        return opened
    }

    @Test
    fun `S15P31A207-278 열린 회가 있고 맨 위가 다른 화면이면 돌아가기를 안내하고 누르면 그 진행 화면을 연다`() = main.runVmTest { vms ->
        val w = world()
        openRound(w)
        val vm = vms.vm(w)
        val opened = opened(vm)
        vm.onForeground(home)
        runCurrent()
        assertEquals(running, vm.state.value.returnTo)
        assertNull(vm.state.value.battle, "그만두기 안내는 띄우지 않는다")
        vm.onReturn()
        runCurrent()
        assertEquals(listOf(running), opened)
        assertNull(vm.state.value.returnTo)
    }

    @Test
    fun `S15P31A207-278 열린 회의 진행 화면이 이미 맨 위면 아무것도 하지 않는다`() = main.runVmTest { vms ->
        val w = world()
        openRound(w)
        val vm = vms.vm(w)
        val opened = opened(vm)
        vm.onForeground(running)
        runCurrent()
        assertNull(vm.state.value.returnTo)
        assertNull(vm.state.value.battle)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `S15P31A207-278 열린 회가 없으면 적어 둔 키가 있어도 돌아가기 대신 그만두기를 안내한다`() = main.runVmTest { vms ->
        val w = world()
        w.screens.remember(running)
        w.battles.activeResult = Outcome.Success(active)
        val vm = vms.vm(w)
        vm.onForeground(home)
        vm.onReturnRequested(9)
        runCurrent()
        assertNull(vm.state.value.returnTo)
        assertEquals(active, vm.state.value.battle)
    }

    @Test
    fun `S15P31A207-278 진행 알림으로 열면 안내 없이 그 대결 진행 화면을 열고 다른 대결 알림은 무시한다`() = main.runVmTest { vms ->
        val w = world()
        openRound(w)
        val vm = vms.vm(w)
        val opened = opened(vm)
        vm.onForeground(home)
        vm.onReturnRequested(5)
        runCurrent()
        assertEquals(emptyList(), opened, "조정자가 모르는 대결")
        vm.onReturnRequested(9)
        runCurrent()
        assertEquals(listOf(running), opened)
        assertNull(vm.state.value.returnTo)
    }

    @Test
    fun `S15P31A207-278 워치에서 시작해 폰 진행 화면이 없는 대결은 건드리지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.coordinator.onMatched(matchFound(battleId = 9), RunStartSpec.StartDevice.WATCH)
        runCurrent()
        w.battles.activeResult = Outcome.Success(active)
        val vm = vms.vm(w)
        val opened = opened(vm)
        vm.onForeground(home)
        vm.onReturnRequested(9)
        runCurrent()
        assertNull(vm.state.value.returnTo)
        assertNull(vm.state.value.battle)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `TC-5908 배치고사 판 이력에는 판 번호를 덧붙인다`() {
        val plain = BattleHistory.Entry(3, BattleHistory.Type.RANDOM, "느긋한고래", Distance(5_000), "WIN", start)
        assertEquals(UiText.Res(R.string.battle_history_type_battle), historyTypeText(plain))
        assertEquals(
            UiText.Res(
                R.string.battle_history_type_placement,
                listOf(UiText.Res(R.string.battle_history_type_battle), 2)
            ),
            historyTypeText(plain.copy(placementRound = 2))
        )
    }
}
