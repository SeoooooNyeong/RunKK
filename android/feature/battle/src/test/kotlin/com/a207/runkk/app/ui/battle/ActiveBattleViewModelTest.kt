@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 앱을 다시 켰을 때 서버에 남은 대결(배틀 QA 7번): 그만두기로 끝내 새 대결 잠금을 푼다. */
class ActiveBattleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val active = ActiveBattle(9, BattleHistory.Type.RANDOM, "IN_PROGRESS", Distance(1_000), "단단한치타", null)

    private fun ViewModelCloser.vm(w: BattleWorld) = track(ActiveBattleViewModel(w.battles, w.coordinator))

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
}
