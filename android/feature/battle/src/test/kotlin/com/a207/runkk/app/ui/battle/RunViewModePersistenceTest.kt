package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/**
 * 보기 선호([임시:D-성-264], [임시:D-성-265], [임시:FR-성-28]): 처음 값은 트랙이고, 고른 보기는 기기에 저장해 다음 대결에서도 쓴다. 지도를
 * 띄울 수 없을 때는 트랙으로 그릴 뿐 저장값을 바꾸지 않는다. 기기 파일 쪽은 data의 DataStoreSettingsRepositoryTest가 본다.
 */
class RunViewModePersistenceTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val key = Routes.BattleRunning(9, "바람처럼", 5_000, OpponentKind.RANDOM.name)

    @Test
    fun `처음에는 트랙이다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        assertEquals(RunViewMode.TRACK, vm.state.value.viewMode, "읽기 전에도 트랙이다")

        vm.bind(key)
        runCurrent()

        assertEquals(RunViewMode.TRACK, vm.state.value.viewMode)
        assertEquals(emptyList(), w.accounts.viewModeSaves)
    }

    @Test
    fun `고른 보기를 저장하고 다시 읽는다`() = main.runVmTest { vms ->
        val w = world()
        val first = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        first.bind(key)
        runCurrent()

        first.onViewModeChange(RunViewMode.MAP)
        runCurrent()
        assertEquals(RunViewMode.MAP, first.state.value.viewMode)
        assertEquals(listOf(RunViewMode.MAP), w.accounts.viewModeSaves)
        first.onViewModeChange(RunViewMode.MAP)
        runCurrent()
        assertEquals(listOf(RunViewMode.MAP), w.accounts.viewModeSaves, "같은 보기는 다시 저장하지 않는다")

        val next = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        next.bind(key.copy(battleId = 10))
        runCurrent()
        assertEquals(RunViewMode.MAP, next.state.value.viewMode, "다음 대결에서도 고른 보기로 연다")
    }

    @Test
    fun `지도 실패 때 저장값은 바뀌지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.accounts.viewMode = RunViewMode.MAP
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        vm.bind(key)
        runCurrent()

        // 화면은 지도 오류를 지역 상태로만 들고 트랙으로 그린다. ViewModel과 저장값은 건드리지 않는다.
        assertEquals(RunViewMode.TRACK, shownViewMode(vm.state.value.viewMode, isMapAvailable = false))
        assertEquals(RunViewMode.MAP, shownViewMode(vm.state.value.viewMode, isMapAvailable = true))
        assertEquals(RunViewMode.MAP, vm.state.value.viewMode)
        assertEquals(RunViewMode.MAP, w.accounts.viewMode)
        assertEquals(emptyList(), w.accounts.viewModeSaves)
    }
}
